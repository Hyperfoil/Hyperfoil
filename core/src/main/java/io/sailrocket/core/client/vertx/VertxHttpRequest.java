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
import io.sailrocket.core.client.AbstractHttpRequest;
import io.sailrocket.core.util.AsyncSemaphore;
import io.sailrocket.spi.HttpHeader;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientRequest;

import java.util.HashMap;
import java.util.Map;

/**
 * @author <a href="mailto:stalep@gmail.com">Ståle Pedersen</a>
 */
public class VertxHttpRequest extends AbstractHttpRequest {
    private final ByteBuf body;
    private Map<String, String> headers;
    private final HttpMethod method;
    private final String path;
    private final ContextAwareClient current;
    private final AsyncSemaphore concurrencyLimiter;

    VertxHttpRequest(HttpMethod method, String path, AsyncSemaphore concurrencyLimiter,
                     ContextAwareClient current, ByteBuf body) {
      this.method = method;
      this.path = path;
      this.concurrencyLimiter = concurrencyLimiter;
      this.current = current;
      this.body = body;
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
        concurrencyLimiter.acquire(() ->
            current.context.runOnContext(v -> {
                HttpClientRequest request = current.client.request(method.vertx, path);
                requestHandler(request);
                if (body != null) {
                    request.end(Buffer.buffer(body));
                } else {
                    request.end();
                }
        }));
    }

    private void requestHandler(HttpClientRequest request) {
        request.handler(response -> {
            try {
                if (statusHandler != null)
                    statusHandler.accept(response.statusCode());
                if (headerHandler != null)
                    headerHandler.accept(new HttpHeader(response.headers()));
                if (dataHandler != null)
                    response.handler(chunk -> dataHandler.accept(chunk.getByteBuf()));
            } catch (Throwable t) {
                exceptionally(t);
                return;
            }
            response.exceptionHandler(this::exceptionally);
            response.endHandler(nil -> {
                // TODO: use response or change interface!
                try {
                    endHandler.accept(null);
                } catch (Throwable t) {
                    if (exceptionHandler != null) {
                        exceptionHandler.accept(t);
                    }
                } finally {
                    // This can invoke another request so we need to run handlers (e.g. record timing) first
                    concurrencyLimiter.release();
                }
            });
        });
        request.exceptionHandler(this::exceptionally);
    }

    private void exceptionally(Throwable t) {
        try {
            if (exceptionHandler != null) {
                exceptionHandler.accept(t);
            }
        } finally {
            concurrencyLimiter.release();
        }
    }
}
