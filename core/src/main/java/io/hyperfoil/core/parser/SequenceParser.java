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

import io.hyperfoil.core.builders.ScenarioBuilder;
import io.hyperfoil.core.builders.SequenceBuilder;

import java.util.function.BiFunction;

import org.yaml.snakeyaml.events.AliasEvent;
import org.yaml.snakeyaml.events.Event;
import org.yaml.snakeyaml.events.MappingEndEvent;
import org.yaml.snakeyaml.events.MappingStartEvent;
import org.yaml.snakeyaml.events.ScalarEvent;
import org.yaml.snakeyaml.events.SequenceStartEvent;

class SequenceParser implements Parser<ScenarioBuilder> {
    private final BiFunction<ScenarioBuilder, String, SequenceBuilder> builderFunction;

    SequenceParser(BiFunction<ScenarioBuilder, String, SequenceBuilder> builderFunction) {
        this.builderFunction = builderFunction;
    }

    @Override
    public void parse(Context ctx, ScenarioBuilder target) throws ParserException {
        ctx.parseList(target, this::parseSequence);
    }

    private void parseSequence(Context ctx, ScenarioBuilder target) throws ParserException {
        ctx.expectEvent(MappingStartEvent.class);
        ScalarEvent sequenceNameEvent = ctx.expectEvent(ScalarEvent.class);
        SequenceBuilder sequenceBuilder = builderFunction.apply(target, sequenceNameEvent.getValue());
        parseSequence(ctx, sequenceBuilder);
        ctx.expectEvent(MappingEndEvent.class);
    }

    static void parseSequence(Context ctx, SequenceBuilder sequenceBuilder) throws ParserException {
        Event event = ctx.peek();
        if (event instanceof SequenceStartEvent) {
            String anchor = ((SequenceStartEvent) event).getAnchor();
            if (anchor != null) {
                ctx.setAnchor(event, anchor, sequenceBuilder);
            }
            ctx.parseList(sequenceBuilder, StepParser.instance());
        } else if (event instanceof ScalarEvent) {
            String value = ((ScalarEvent) event).getValue();
            if (value == null || value.isEmpty()) {
                throw new ParserException(event, "The sequence must not be empty.");
            } else {
                throw new ParserException(event, "Expected sequence of steps but got '" + value + "'");
            }
        } else if (event instanceof AliasEvent) {
            String anchor = ((AliasEvent) event).getAnchor();
            SequenceBuilder sequence = ctx.getAnchor(event, anchor, SequenceBuilder.class);
            sequenceBuilder.readFrom(sequence);
            ctx.consumePeeked(event);
        } else {
            throw ctx.unexpectedEvent(event);
        }
    }

}
