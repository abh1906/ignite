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

package org.gridgain.examples.compute;

import org.apache.ignite.*;
import org.apache.ignite.lang.*;
import org.apache.ignite.scheduler.*;
import org.gridgain.examples.*;

import java.util.concurrent.*;

/**
 * Demonstrates a cron-based {@link Runnable} execution scheduling.
 * Test runnable object broadcasts a phrase to all grid nodes every minute
 * three times with initial scheduling delay equal to five seconds.
 * <p>
 * Remote nodes should always be started with special configuration file which
 * enables P2P class loading: {@code 'ggstart.{sh|bat} examples/config/example-compute.xml'}.
 * <p>
 * Alternatively you can run {@link ComputeNodeStartup} in another JVM which will start GridGain node
 * with {@code examples/config/example-compute.xml} configuration.
 */
public class ComputeScheduleExample {
    /**
     * Executes example.
     *
     * @param args Command line arguments, none required.
     * @throws IgniteCheckedException If example execution failed.
     */
    public static void main(String[] args) throws IgniteCheckedException {
        try (Ignite g = Ignition.start("examples/config/example-compute.xml")) {
            System.out.println();
            System.out.println("Compute schedule example started.");

            // Schedule output message every minute.
            SchedulerFuture<?> fut = g.scheduler().scheduleLocal(
                new Callable<Integer>() {
                    private int invocations;

                    @Override public Integer call() {
                        invocations++;

                        try {
                            g.compute().broadcast(
                                new IgniteRunnable() {
                                    @Override public void run() {
                                        System.out.println();
                                        System.out.println("Howdy! :) ");
                                    }
                                }
                            );
                        }
                        catch (IgniteCheckedException e) {
                            throw new IgniteException(e);
                        }

                        return invocations;
                    }
                },
                "{5, 3} * * * * *" // Cron expression.
            );

            while (!fut.isDone())
                System.out.println(">>> Invocation #: " + fut.get());

            System.out.println();
            System.out.println(">>> Schedule future is done and has been unscheduled.");
            System.out.println(">>> Check all nodes for hello message output.");
        }
    }
}
