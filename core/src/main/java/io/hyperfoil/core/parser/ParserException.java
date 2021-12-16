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
package io.hyperfoil.core.parser;

import org.yaml.snakeyaml.events.Event;

import io.hyperfoil.api.config.BenchmarkSource;
import io.hyperfoil.api.config.Locator;

public class ParserException extends Exception {
   public ParserException(String msg) {
      this(msg, null);
   }

   public ParserException(String msg, Exception e) {
      super(Locator.isAvailable() ? Locator.current().locationMessage() + ": " + msg : msg, e);
   }

   public ParserException(Event event, String msg) {
      this(event, msg, null);
   }

   public ParserException(Event event, String msg, Throwable cause) {
      super(location(event, msg), cause);
   }

   static String location(Event event, String msg) {
      StringBuilder lineInfo = location(event);
      lineInfo.append(": ").append(msg);
      String source = null;
      if (Locator.isAvailable()) {
         BenchmarkSource bs = Locator.current().benchmark().source();
         source = bs == null ? null : bs.yaml;
      }
      if (source != null) {
         lineInfo.append("; See below: \n");
         String[] lines = source.split("\n");
         int current = event.getStartMark().getLine() + 1;
         int min = Math.max(1, current - 2);
         for (int i = min; i <= current; ++i) {
            lineInfo.append(lines[i - 1]).append('\n');
         }
         for (int i = event.getStartMark().getColumn(); i > 0; --i) {
            lineInfo.append(' ');
         }
         lineInfo.append("^ HERE\n");
         for (int i = 0; i < 2; ++i) {
            lineInfo.append(lines[current + i]).append('\n');
         }
      }
      return lineInfo.toString();
   }

   static StringBuilder location(Event event) {
      StringBuilder lineInfo = new StringBuilder("line ").append(event.getStartMark().getLine() + 1).append(", column ").append(event.getStartMark().getColumn() + 1);
      if (Locator.isAvailable()) {
         lineInfo.append(": ").append(Locator.current().locationMessage());
      }
      return lineInfo;
   }
}
