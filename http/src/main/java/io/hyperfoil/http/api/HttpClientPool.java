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
package io.hyperfoil.http.api;

import io.hyperfoil.core.impl.ConnectionStatsConsumer;
import io.netty.util.concurrent.EventExecutor;
import io.hyperfoil.http.config.Http;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

/**
 * Manages access to single host (identified by the same URL), keeping a {@link HttpConnectionPool} for each executor.
 */
public interface HttpClientPool {

   Http config();

   void start(Handler<AsyncResult<Void>> completionHandler);

   void shutdown();

   HttpConnectionPool next();

   HttpConnectionPool connectionPool(EventExecutor executor);

   String host();

   String authority();

   byte[] originalDestinationBytes();

   String scheme();

   boolean isSecure();

   void visitConnectionStats(ConnectionStatsConsumer consumer);
}
