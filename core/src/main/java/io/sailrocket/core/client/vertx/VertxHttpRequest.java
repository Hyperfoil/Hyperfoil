/*
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

package io.sailrocket.core.client.vertx;

import io.netty.buffer.ByteBuf;
import io.sailrocket.api.HttpMethod;
import io.sailrocket.api.HttpRequest;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * @author <a href="mailto:stalep@gmail.com">Ståle Pedersen</a>
 */
public class VertxHttpRequest implements HttpRequest {

    private Map<String, String> headers;
    private final HttpMethod method;
    private final String path;
    private IntConsumer statusHandler;
    private Consumer<byte[]> dataHandler;
    private IntConsumer resetHandler;
    private Consumer<Void> endHandler;
    private final Slot current;
    private final AtomicInteger inflight;

    VertxHttpRequest(HttpMethod method, String path, AtomicInteger inflight,
                     Slot current) {
      this.method = method;
      this.path = path;
      this.inflight = inflight;
      this.current = current;
    }
    @Override
    public HttpRequest putHeader(String name, String value) {
      if (headers == null) {
        headers = new HashMap<>();
      }
      headers.put(name, value);
      return this;
    }
    @Override
    public HttpRequest statusHandler(IntConsumer handler) {
      statusHandler = handler;
      return this;
    }

    @Override
    public HttpRequest headerHandler(Consumer<Map<String,String>> handler) {
        //TODO
        return this;
    }

    @Override
    public HttpRequest resetHandler(IntConsumer handler) {
      resetHandler = handler;
      return this;
    }

    @Override
    public HttpRequest bodyHandler(Consumer<byte[]> handler) {
        this.dataHandler = handler;
        return this;
    }

    @Override
    public HttpRequest endHandler(Consumer<Void> handler) {
      endHandler = handler;
      return this;
    }

    @Override
    public void end(ByteBuf buff) {
        current.context.runOnContext(v -> {
            HttpClientRequest request = current.client.request(method.vertx, path);
            requestHandler(request);
            if (buff != null) {
                request.end(Buffer.buffer(buff));
            }
            else {
                request.end();
            }
        });
    }

    private void requestHandler(HttpClientRequest request) {
        Future<HttpClientResponse> fut = Future.future();
        Future<Void> doneHandler = Future.future();
        doneHandler.setHandler(ar -> {
          inflight.decrementAndGet();
          if (ar.succeeded()) {
            endHandler.accept(null);
          }
        });
        fut.setHandler(ar -> {
          if (ar.succeeded()) {
            HttpClientResponse resp = ar.result();
            statusHandler.accept(resp.statusCode());
            resp.exceptionHandler(fut::tryFail);
            resp.endHandler(doneHandler::tryComplete);
            Consumer<byte[]> handler = this.dataHandler;
            if (handler != null) {
              resp.handler(chunk -> handler.accept(chunk.getByteBuf().array()));
            }
          } else {
            doneHandler.fail(ar.cause());
          }
        });
        request.handler(fut::tryComplete);
        request.exceptionHandler(fut::tryFail);
     }

}
