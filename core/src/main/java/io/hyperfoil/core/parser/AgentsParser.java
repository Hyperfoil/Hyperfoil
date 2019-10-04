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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import io.hyperfoil.api.config.BenchmarkBuilder;

import org.yaml.snakeyaml.events.Event;
import org.yaml.snakeyaml.events.MappingEndEvent;
import org.yaml.snakeyaml.events.MappingStartEvent;
import org.yaml.snakeyaml.events.ScalarEvent;
import org.yaml.snakeyaml.events.SequenceEndEvent;
import org.yaml.snakeyaml.events.SequenceStartEvent;

class AgentsParser implements Parser<BenchmarkBuilder> {

   @Override
   public void parse(Context ctx, BenchmarkBuilder builder) throws ParserException {
      Event event = ctx.next();
      if (event instanceof ScalarEvent) {
         String value = ((ScalarEvent) event).getValue();
         if (value == null || value.isEmpty()) {
            // `agents:` without a value should be equal to omitting agents declaration completely
         } else {
            builder.addAgent(value, null, Collections.emptyMap());
         }
      } else if (event instanceof SequenceStartEvent) {
         while (ctx.hasNext()) {
            Event next = ctx.next();
            if (next instanceof SequenceEndEvent) {
               break;
            } else if (next instanceof ScalarEvent) {
               parseAgent(ctx, builder, ((ScalarEvent) next).getValue());
            } else if (next instanceof MappingStartEvent) {
               ScalarEvent nameEvent = ctx.expectEvent(ScalarEvent.class);
               parseAgent(ctx, builder, nameEvent.getValue());
               ctx.expectEvent(MappingEndEvent.class);
            } else {
               throw ctx.unexpectedEvent(next);
            }
         }
      } else if (event instanceof MappingStartEvent) {
         while (ctx.hasNext()) {
            Event next = ctx.next();
            if (next instanceof MappingEndEvent) {
               break;
            } else if (next instanceof ScalarEvent) {
               parseAgent(ctx, builder, ((ScalarEvent) next).getValue());
            } else {
               throw ctx.unexpectedEvent(next);
            }
         }
      } else {
         throw ctx.unexpectedEvent(event);
      }
   }

   private void parseAgent(Context ctx, BenchmarkBuilder builder, String name) throws ParserException {
      if (!ctx.hasNext()) {
         builder.addAgent(name, null, null);
         return;
      }
      Event next = ctx.peek();
      if (next instanceof ScalarEvent) {
         builder.addAgent(name, ctx.expectEvent(ScalarEvent.class).getValue(), Collections.emptyMap());
      } else if (next instanceof MappingStartEvent) {
         ctx.expectEvent(MappingStartEvent.class);
         Map<String, String> properties = new HashMap<>();
         while (ctx.hasNext()) {
            Event event = ctx.next();
            if (event instanceof MappingEndEvent) {
               break;
            } else if (event instanceof ScalarEvent) {
               String propertyName = ((ScalarEvent) event).getValue();
               String propertyValue = ctx.expectEvent(ScalarEvent.class).getValue();
               properties.put(propertyName, propertyValue);
            } else {
               throw ctx.unexpectedEvent(event);
            }
         }
         builder.addAgent(name, null, properties);
      } else {
         builder.addAgent(name, null, Collections.emptyMap());
      }
   }

}
