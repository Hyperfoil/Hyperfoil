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
package io.sailrocket.api;

import io.netty.buffer.ByteBuf;
import io.sailrocket.spi.HttpHeader;

import java.util.function.Consumer;
import java.util.function.IntConsumer;

public interface HttpRequest {

  /**
   * Adds a key value pair to the request header
   *
   * @param name key
   * @param value value
   * @return this
   */
  HttpRequest putHeader(String name, String value);

  /**
   * Http status codes, ref:
   * https://en.wikipedia.org/wiki/List_of_HTTP_status_codes
   * @param handler statusHandler
   * @return this
   */
  HttpRequest statusHandler(IntConsumer handler);

  /**
   * Handles headers
   * @param handler headerHandler
   *
   * @return this
   */
  HttpRequest headerHandler(Consumer<HttpHeader> handler);

  HttpRequest resetHandler(IntConsumer handler);

  HttpRequest bodyHandler(Consumer<byte[]> handler);

  HttpRequest endHandler(Consumer<HttpResponse> handler);

  HttpRequest exceptionHandler(Consumer<Throwable> handler);

  void end(ByteBuf buff);

  default void end() {
    end(null);
  }
}
