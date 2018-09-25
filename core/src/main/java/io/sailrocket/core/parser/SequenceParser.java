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
package io.sailrocket.core.parser;

import io.sailrocket.core.builders.ScenarioBuilder;
import io.sailrocket.core.builders.SequenceBuilder;

import java.util.function.BiFunction;

import org.yaml.snakeyaml.events.MappingEndEvent;
import org.yaml.snakeyaml.events.ScalarEvent;
import org.yaml.snakeyaml.events.SequenceStartEvent;

class SequenceParser implements Parser<ScenarioBuilder> {
    private final BiFunction<ScenarioBuilder, String, SequenceBuilder> builderFunction;

    SequenceParser(BiFunction<ScenarioBuilder, String, SequenceBuilder> builderFunction) {
        this.builderFunction = builderFunction;
    }

    @Override
    public void parse(Context ctx, ScenarioBuilder target) throws ConfigurationParserException {
        ctx.parseList(target, this::parseSequence);
    }

    private void parseSequence(Context ctx, ScenarioBuilder target) throws ConfigurationParserException {
        ScalarEvent event = ctx.expectEvent(ScalarEvent.class);
        SequenceBuilder sequenceBuilder = builderFunction.apply(target, event.getValue());
        ctx.expectEvent(SequenceStartEvent.class);
        ctx.parseListHeadless(sequenceBuilder, this::parseSequenceItem, (event1, sequenceBuilder1) -> parseSingleItem(event1, sequenceBuilder1));
        ctx.expectEvent(MappingEndEvent.class);
    }

    private void parseSequenceItem(Context ctx, SequenceBuilder sequenceBuilder) throws ConfigurationParserException {
        StepParser.instance().parse(ctx, sequenceBuilder);
    }

    private void parseSingleItem(ScalarEvent event, SequenceBuilder sequenceBuilder) throws ConfigurationParserException {
        StepParser.instance().parseSingle(event, sequenceBuilder);
    }
}
