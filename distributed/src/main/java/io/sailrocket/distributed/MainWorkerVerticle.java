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

package io.sailrocket.distributed;

import io.sailrocket.distributed.util.WorkerMessage;
import io.sailrocket.distributed.util.WorkerMessageCodec;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;

import java.util.logging.Logger;

public class MainWorkerVerticle extends AbstractVerticle {

  private final VerticleWorker verticleWorker;
  private String workerId;

  private static final Logger LOG = Logger.getLogger(MainWorkerVerticle.class.getName());

  public MainWorkerVerticle(VerticleWorker verticleWorker) {
    this.verticleWorker = verticleWorker;

  }

  public void init(Future<Void> start) {
    vertx = Vertx.vertx();
    vertx.eventBus().registerDefaultCodec(WorkerMessage.class, new WorkerMessageCodec());
    vertx.deployVerticle(this, deploymentResult -> {
      if(deploymentResult.succeeded()) {
          LOG.info("MainWorker got id: "+deploymentID());
          start.complete();
      }
      else
          start.fail(deploymentResult.cause());
    });
  }

  @Override
  public void start(Future<Void> startFuture) {
      if(verticleWorker == null) {
          startFuture.fail("VerticleWorker is null!");
      }
      else {
          vertx.deployVerticle(verticleWorker, deploymentResult -> {
              if (deploymentResult.succeeded()) {
                  workerId = deploymentResult.result();
                  startFuture.complete();
              }
              else {
                  startFuture.fail(deploymentResult.cause());
              }
          });
      }
  }

  public void stop(Future<Void> stopFuture) {
      vertx.undeploy(workerId, result -> {
          if(result.succeeded()) {
              LOG.info("Undeployed Worker: " + workerId);
              stopFuture.complete();
          }
          else
              stopFuture.fail(result.cause());
      });
  }

        /*
        getVertx().setPeriodic(2000, _id -> {
          eventBus.send("ping", pingMessage, reply -> {
            if (reply.succeeded()) {
              PingMessage pingMessage = (PingMessage) reply.result().body();
              LOG.info("Received local reply: "+pingMessage.getSummary());
            } else {
              LOG.info("No reply from local receiver");
            }
          });
        });
        */

    //not sure if this method should be public...
  public void updateWorkerStatus(WorkerStatus status, Handler<WorkerMessage> messageHandler) {
      vertx.eventBus().send(verticleWorker.name(), new WorkerMessage(status, deploymentID()), (AsyncResult<Message<WorkerMessage>> reply) -> {
        if(reply.succeeded()) {
            messageHandler.handle(reply.result().body());
        }
        else
          reply.cause().printStackTrace();
      });
  }

}
