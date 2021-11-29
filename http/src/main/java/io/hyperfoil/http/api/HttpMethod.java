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

import io.hyperfoil.api.config.BuilderBase;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.function.SerializableFunction;

public enum HttpMethod {

   GET, HEAD, POST, PUT, DELETE, OPTIONS, PATCH, TRACE, CONNECT;

   public final io.netty.handler.codec.http.HttpMethod netty;

   HttpMethod() {
      this.netty = io.netty.handler.codec.http.HttpMethod.valueOf(name());
   }

   @FunctionalInterface
   public interface Builder extends BuilderBase<Builder> {
      SerializableFunction<Session, HttpMethod> build();
   }

   public static class Provided implements SerializableFunction<Session, HttpMethod> {
      private final HttpMethod method;

      public Provided(HttpMethod method) {
         this.method = method;
      }

      @Override
      public HttpMethod apply(Session o) {
         return method;
      }
   }

   public static class ProvidedBuilder implements HttpMethod.Builder {
      private HttpMethod method;

      public ProvidedBuilder(ProvidedBuilder other) {
         this.method = other.method;
      }

      public ProvidedBuilder(HttpMethod method) {
         this.method = method;
      }

      @Override
      public SerializableFunction<Session, HttpMethod> build() {
         return new Provided(method);
      }

      @Override
      public String toString() {
         return method.name();
      }
   }
}
