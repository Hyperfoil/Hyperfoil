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

import io.hyperfoil.api.config.ScenarioBuilder;

import org.yaml.snakeyaml.events.AliasEvent;
import org.yaml.snakeyaml.events.MappingStartEvent;
import org.yaml.snakeyaml.events.SequenceStartEvent;

class ScenarioParser extends AbstractParser<ScenarioBuilder, ScenarioBuilder> {
   ScenarioParser() {
      register("initialSequences", new SequenceParser(ScenarioBuilder::initialSequence));
      register("sequences", new SequenceParser(ScenarioBuilder::sequence));
      register("orderedSequences", new OrderedSequenceParser());
      register("maxSequences", new PropertyParser.Int<>(ScenarioBuilder::maxSequences));
      register("maxRequests", new PropertyParser.Int<>(ScenarioBuilder::maxRequests));
   }

   @Override
   public void parse(Context ctx, ScenarioBuilder target) throws ParserException {
      if (!ctx.hasNext()) {
         throw ctx.noMoreEvents(MappingStartEvent.class, AliasEvent.class, SequenceStartEvent.class);
      }
      if (ctx.peek() instanceof SequenceStartEvent) {
         new OrderedSequenceParser().parse(ctx, target);
      } else {
         callSubBuilders(ctx, target);
      }
   }
}
