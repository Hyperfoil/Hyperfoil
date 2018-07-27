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

package io.sailrocket.api;

import io.sailrocket.core.workers.VerticleWorker;
import io.sailrocket.distributed.util.WorkerMessage;
import io.vertx.core.Future;

import java.util.logging.Logger;

public class HttpVerticle extends VerticleWorker {

    public static final String HTTP_SERVICE = "http-service";

    private static final Logger LOG = Logger.getLogger(HttpVerticle.class.getName());

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        vertx.eventBus().consumer(HTTP_SERVICE, message -> {

            WorkerMessage workerMessage = (WorkerMessage) message.body();
            LOG.info("HTTP received message: "+workerMessage.toString());
            //we should do some work here
            message.reply(new WorkerMessage(workerMessage.statusCode(), deploymentID()));
        });
        startFuture.complete();
    }

    private void parseWorkerMessage(WorkerMessage message) throws InterruptedException {
        //lets just simulate that we're doing some work..
        LOG.info("parsing: "+message.statusCode()+" and working a bit...");
        Thread.sleep(300);
    }

    @Override
    public void stop(Future<Void> stopFuture) throws Exception {
        //we might have some stuff to do before we stop..
        //but for now we just finish
        stopFuture.complete();
    }


    @Override
    public String name() {
        return HTTP_SERVICE;
    }
}
