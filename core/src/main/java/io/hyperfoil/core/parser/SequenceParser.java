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

import io.hyperfoil.api.config.Locator;
import io.hyperfoil.api.config.ScenarioBuilder;
import io.hyperfoil.api.config.SequenceBuilder;

import org.yaml.snakeyaml.events.Event;
import org.yaml.snakeyaml.events.MappingEndEvent;
import org.yaml.snakeyaml.events.MappingStartEvent;
import org.yaml.snakeyaml.events.ScalarEvent;
import org.yaml.snakeyaml.events.SequenceStartEvent;

class SequenceParser implements Parser<ScenarioBuilder> {
   private final Supplier supplier;

   SequenceParser(Supplier supplier) {
      this.supplier = supplier;
   }

   @Override
   public void parse(Context ctx, ScenarioBuilder target) throws ParserException {
      ctx.parseList(target, this::parseSequence);
   }

   private void parseSequence(Context ctx, ScenarioBuilder target) throws ParserException {
      ctx.expectEvent(MappingStartEvent.class);
      ScalarEvent sequenceNameEvent = ctx.expectEvent(ScalarEvent.class);
      parseSequence(ctx, sequenceNameEvent.getValue(), target, supplier);
      ctx.expectEvent(MappingEndEvent.class);
   }

   static SequenceBuilder parseSequence(Context ctx, String name, ScenarioBuilder scenario, Supplier supplier) throws ParserException {
      Event event = ctx.peek();
      if (event instanceof SequenceStartEvent) {
         SequenceBuilder sequence = supplier.get(scenario, name, null);
         Locator.push(null, sequence);
         try {
            ctx.parseList(sequence, StepParser.instance());
         } finally {
            Locator.pop();
         }
         return sequence;
      } else if (event instanceof ScalarEvent) {
         String value = ((ScalarEvent) event).getValue();
         if (value == null || value.isEmpty()) {
            throw new ParserException(event, "The sequence must not be empty.");
         } else {
            throw new ParserException(event, "Expected sequence of steps but got '" + value + "'");
         }
      } else {
         throw ctx.unexpectedEvent(event);
      }
   }

   interface Supplier {
      SequenceBuilder get(ScenarioBuilder scenario, String name, SequenceBuilder copy);
   }

}
