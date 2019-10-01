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

public class ParserException extends Exception {
   public ParserException(String msg) {
      super(msg);
   }

   public ParserException(String msg, Exception e) {
      super(msg, e);
   }

   public ParserException(Event event, String msg) {
      this(event, msg, null);
   }

   public ParserException(Event event, String msg, Throwable cause) {
      super(location(event) + ": " + msg, cause);
   }

   static String location(Event event) {
      return "line " + (event.getStartMark().getLine() + 1) + ", column " + (event.getStartMark().getColumn() + 1);
   }
}
