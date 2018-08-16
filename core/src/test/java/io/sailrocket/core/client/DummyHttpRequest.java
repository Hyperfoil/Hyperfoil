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
 *
 */

package io.sailrocket.core.client;

import io.sailrocket.api.HttpMethod;
import io.sailrocket.api.HttpRequest;
import io.sailrocket.core.client.vertx.ContextAwareClient;
import io.vertx.core.Future;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="mailto:stalep@gmail.com">Ståle Pedersen</a>
 */
public class DummyHttpRequest extends AbstractHttpRequest {

    private Map<String, String> headers;
    private final HttpMethod method;
    private final String path;
    private final ContextAwareClient current;
    private final AtomicInteger inflight;

   public DummyHttpRequest(HttpMethod method, String path, AtomicInteger inflight,
                     ContextAwareClient current) {
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
    public void end() {
        endHandler.run();
        /*
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
        */
    }

    private void requestHandler(HttpClientRequest request) {
        Future<HttpClientResponse> fut = Future.future();
        Future<Void> doneHandler = Future.future();
        doneHandler.setHandler(ar -> {
            inflight.decrementAndGet();
            if (ar.succeeded()) {
                endHandler.run();
            }
        });
        fut.setHandler(ar -> {
            if (ar.succeeded()) {
                HttpClientResponse resp = ar.result();
                if(statusHandler != null)
                    statusHandler.accept(resp.statusCode());
                if(headerHandler != null) {
                    for (Map.Entry<String, String> header : resp.headers()) {
                       headerHandler.accept(header.getKey(), header.getValue());
                    }
                }
                if(dataHandler != null)
                    resp.handler(chunk -> dataHandler.accept(chunk.getByteBuf()));
                resp.exceptionHandler(fut::tryFail);
                resp.endHandler(doneHandler::tryComplete);
            }
            else {
                doneHandler.fail(ar.cause());
            }
        });
        request.handler(fut::tryComplete);
        request.exceptionHandler(fut::tryFail);
    }

}
