/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gridgain.examples.streaming;

import org.apache.ignite.*;
import org.apache.ignite.lang.*;
import org.apache.ignite.streamer.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Example to demonstrate how to compute a running average. In this example
 * random numbers are being streamed into the system and the streamer
 * continuously maintains a running average over last {@code 500} numbers.
 * <p>
 * Remote nodes should always be started with special configuration file:
 * {@code 'ggstart.{sh|bat} examples/config/example-streamer.xml'}.
 * When starting nodes this way JAR file containing the examples code
 * should be placed to {@code GRIDGAIN_HOME/libs} folder. You can build
 * {@code gridgain-examples.jar} by running {@code mvn package} in
 * {@code GRIDGAIN_HOME/examples} folder. After that {@code gridgain-examples.jar}
 * will be generated by Maven in {@code GRIDGAIN_HOME/examples/target} folder.
 * <p>
 * Alternatively you can run {@link StreamingNodeStartup} in another JVM which will start GridGain node
 * with {@code examples/config/example-streamer.xml} configuration.
 */
public class StreamingRunningAverageExample {
    /**
     * Main method.
     *
     * @param args Parameters.
     * @throws Exception If failed.
     */
    public static void main(String[] args) throws Exception {
        Ignite ignite = Ignition.start("examples/config/example-streamer.xml");

        System.out.println();
        System.out.println(">>> Streaming running average example started.");

        final IgniteStreamer streamer = ignite.streamer("running-average");

        final int rndRange = 100;

        // This thread executes a query across all nodes
        // to collect a running average from all of them.
        // During reduce step the results are collected
        // and reduced into one average value.
        Thread qryThread = new Thread(new Runnable() {
            @SuppressWarnings("BusyWait")
            @Override public void run() {
                while (!Thread.interrupted()) {
                    try {
                        try {
                            Thread.sleep(3000);
                        }
                        catch (InterruptedException ignore) {
                            return;
                        }

                        // Running average.
                        double avg = streamer.context().reduce(
                            new IgniteClosure<StreamerContext, Average>() {
                                @Override public Average apply(StreamerContext ctx) {
                                    return ctx.<String, Average>localSpace().get("avg");
                                }
                            },
                            new IgniteReducer<Average, Double>() {
                                private Average avg = new Average();

                                @Override public boolean collect(@Nullable Average a) {
                                    if (a != null)
                                        avg.add(a);

                                    return true;
                                }

                                @Override public Double reduce() {
                                    return avg.average();
                                }
                            }
                        );

                        System.out.println("Got streamer query result [avg=" + avg + ", idealAvg=" + (rndRange / 2) + ']');
                    }
                    catch (IgniteCheckedException e) {
                        System.out.println("Failed to execute streamer query: " + e);
                    }
                }
            }
        });

        // This thread continuously stream events
        // into the system.
        Thread evtThread = new Thread(new Runnable() {
            @Override public void run() {
                Random rnd = new Random();

                while (!Thread.interrupted()) {
                    try {
                        streamer.addEvent(rnd.nextInt(rndRange));
                    }
                    catch (IgniteCheckedException e) {
                        System.out.println("Failed to add streamer event: " + e);
                    }
                }
            }
        });

        try {
            System.out.println(">>> Starting streamer query and producer threads. Press enter to stop this example.");

            qryThread.start();
            evtThread.start();

            try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in))) {
                in.readLine();
            }
        }
        finally {
            qryThread.interrupt();
            evtThread.interrupt();

            qryThread.join();
            evtThread.join();

            Ignition.stopAll(false);
        }
    }

    /**
     * Sample streamer stage to compute average.
     */
    public static class StreamerStage implements org.apache.ignite.streamer.StreamerStage<Integer> {
        /** {@inheritDoc} */
        @Override public String name() {
            return "exampleStage";
        }

        /** {@inheritDoc} */
        @Nullable @Override public Map<String, Collection<?>> run(StreamerContext ctx, Collection<Integer> evts)
            throws IgniteCheckedException {
            ConcurrentMap<String, Average> loc = ctx.localSpace();

            Average avg = loc.get("avg");

            // Store average in local space if it was not done before.
            if (avg == null) {
                Average old = loc.putIfAbsent("avg", avg = new Average());

                if (old != null)
                    avg = old;
            }

            // For every input event, update the average.
            for (Integer e : evts)
                avg.add(e, 1);

            StreamerWindow<Integer> win = ctx.window();

            // Add input events to window.
            win.enqueueAll(evts);

            while (true) {
                Integer e = win.pollEvicted();

                if (e == null)
                    break;

                // Subtract evicted events from running average.
                avg.add(-e, -1);
            }

            return null;
        }
    }

    /**
     * Class to help calculate average.
     */
    public static class Average {
        /** */
        private int total;

        /** */
        private int cnt;

        /**
         * Adds one average to another.
         *
         * @param avg Average to add.
         */
        public void add(Average avg) {
            int total;
            int cnt;

            synchronized (avg) {
                total = avg.total;
                cnt = avg.cnt;
            }

            add(total, cnt);
        }

        /**
         * Adds passed in values to current values.
         * <p>
         * Note that this method is synchronized because multiple
         * threads will be updating the same average instance concurrently.
         *
         * @param total Total delta.
         * @param cnt Count delta.
         */
        public synchronized void add(int total, int cnt) {
            this.total += total;
            this.cnt += cnt;
        }

        /**
         * Calculates current average based on total value and count.
         * <p>
         * Note that this method is synchronized because multiple
         * threads will be updating the same average instance concurrently.

         * @return Running average.
         */
        public synchronized double average() {
            return (double)total / cnt;
        }
    }
}
