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
package io.sailrocket.core.client.vertx;

import io.sailrocket.api.HttpClientPool;
import io.sailrocket.api.HttpMethod;
import io.sailrocket.api.HttpRequest;
import io.sailrocket.core.util.AsyncSemaphore;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientOptions;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * @author <a href="mailto:stalep@gmail.com">Ståle Pedersen</a>
 */
public class VertxHttpClientPool implements HttpClientPool {
  private final Vertx vertx;
  private final int maxInflight;
  private AtomicInteger currentSlot = new AtomicInteger();
  private ContextAwareClient[] contextAwareClients;
  private final ThreadLocal<ContextAwareClient> current = ThreadLocal.withInitial(() -> contextAwareClients[currentSlot.getAndIncrement() % contextAwareClients.length]);
  private final AsyncSemaphore concurrencyLimiter;

  public VertxHttpClientPool(VertxHttpClientPoolFactory builder) {

    HttpClientOptions options = new HttpClientOptions()
        .setSsl(builder.ssl)
        .setTrustAll(true)
        .setVerifyHost(false)
        .setKeepAlive(true)
        .setPipeliningLimit(builder.concurrency)
        .setPipelining(true)
        .setDefaultPort(builder.port)
        .setDefaultHost(builder.host);

    this.vertx = builder.vertx;
    this.maxInflight = builder.concurrency * builder.size;
    this.concurrencyLimiter = new AsyncSemaphore(maxInflight);
    this.contextAwareClients = new ContextAwareClient[builder.threadCount];

    int perSlotSize = builder.size / contextAwareClients.length;
    for (int i = 0; i < contextAwareClients.length; i++) {
      int n = perSlotSize;
      if (i == 0) {
        n += builder.size % contextAwareClients.length;
      }
      contextAwareClients[i] = new ContextAwareClient(vertx.createHttpClient(new HttpClientOptions(options).setMaxPoolSize(n)), vertx.getOrCreateContext());
    }

  }

  @Override
  public void start(Consumer<Void> completionHandler) {
    completionHandler.accept(null);
  }

  @Override
  public HttpRequest request(HttpMethod method, String path) {
      //TODO:: this needs to return a connection in a pool
      //atm this tightly couples requests to the connection "pool"
      //we aren't really pooling
      return new VertxHttpRequest(method, path, concurrencyLimiter, current.get());
  }

  @Override
  public long bytesRead() {
    return 0;
  }

  @Override
  public long bytesWritten() {
    return 0;
  }

  @Override
  public void resetStatistics() {
  }

  @Override
  public void shutdown() {
  }
}
