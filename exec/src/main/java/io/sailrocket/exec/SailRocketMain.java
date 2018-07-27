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

import io.sailrocket.api.HttpVerticle;
import io.sailrocket.distributed.MainWorkerVerticle;
import io.sailrocket.distributed.VerticleWorker;
import io.sailrocket.distributed.WorkerStatus;
import io.vertx.core.Future;

import java.util.logging.Logger;

/**
 *
 */
public class SailRocketMain {

    private MainWorkerVerticle mainWorkerVerticle;
    private boolean RUNNING = false;

    private static final Logger LOG = Logger.getLogger(SailRocketMain.class.getName());

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
        LOG.info("Setup complete, starting benchmark...");
        //lets do some simple stuff and finish for now
        RUNNING = true;

        mainWorkerVerticle.updateWorkerStatus(WorkerStatus.START_RAMPUP, rampUpHandler -> {
            if(rampUpHandler != null) {
                LOG.info("received ramp up reply from HTTP: " + rampUpHandler);
                //lets do another message before we end..
                mainWorkerVerticle.updateWorkerStatus(WorkerStatus.END_RAMPUP, rampDownHandler -> {
                    if(rampDownHandler != null) {
                        LOG.info("received ramp down reply from HTTP: " + rampUpHandler);
                        RUNNING = false;
                        LOG.info("finished run, calling end");
                        stop();
                    }
                });
            }
            else {
                LOG.info("reply from worker was null, handle it..");
                LOG.info("atm we're just quitting");
                stop();

            }
        });
    }

    public void stop() {
       //undeploy verticles
        Future<Void> stopFuture = Future.future();
        stopFuture.setHandler( handler -> {
           if(handler.succeeded()) {
               LOG.info("benchmark ended..");
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
