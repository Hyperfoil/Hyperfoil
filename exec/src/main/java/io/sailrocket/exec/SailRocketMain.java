/*
 * JBoss, Home of Professional Open Source
 * Copyright 2018 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.sailrocket.exec;

import io.sailrocket.core.workers.MainWorkerVerticle;
import io.sailrocket.core.workers.VerticleWorker;
import io.sailrocket.core.workers.WorkerStatus;
import io.sailrocket.http.HttpVerticle;
import io.vertx.core.Future;

/**
 *
 */
public class SailRocketMain {

    private MainWorkerVerticle mainWorkerVerticle;
    private boolean RUNNING = false;

   public SailRocketMain() {
        //do get some builder stuff ++
        //send the builder data to MainWorkerVerticle
        //for now we just call start to test vertx
       start(new HttpVerticle());
    }

    public void start(VerticleWorker verticleWorker) {
        mainWorkerVerticle = new MainWorkerVerticle( verticleWorker);
        Future<Void> initFuture = Future.future();
        initFuture.setHandler( handler -> {
           if(handler.succeeded()) {
               RUNNING = true;
               run();
           }
           else
               handler.cause().printStackTrace();
        });
        mainWorkerVerticle.init(initFuture);
    }

    public void run() {
        System.out.println("Setup complete, starting benchmark...");
        //lets do some simple stuff and finish for now
        RUNNING = true;

        mainWorkerVerticle.updateWorkerStatus(WorkerStatus.START_RAMPUP, rampUpHandler -> {
            if(rampUpHandler != null) {
                System.out.println("received ramp up reply from HTTP: " + rampUpHandler);
                //lets do another message before we end..
                mainWorkerVerticle.updateWorkerStatus(WorkerStatus.END_RAMPUP, rampDownHandler -> {
                    if(rampDownHandler != null) {
                        System.out.println("received ramp down reply from HTTP: " + rampUpHandler);
                        RUNNING = false;
                        System.out.println("finished run, calling end");
                        stop();
                    }
                });
            }
            else {
                System.out.println("reply from worker was null, handle it..");
                System.out.println("atm we're just quitting");
                stop();

            }
        });
    }

    public void stop() {
       //undeploy verticles
        Future<Void> stopFuture = Future.future();
        stopFuture.setHandler( handler -> {
           if(handler.succeeded()) {
               System.out.println("benchmark ended..");
               System.exit(1);
           }
           else {
               handler.cause().printStackTrace();
               System.exit(1);
           }
        });
         mainWorkerVerticle.stop(stopFuture);
    }

    public static void main(String[] args) {
       new SailRocketMain();
    }

 }
