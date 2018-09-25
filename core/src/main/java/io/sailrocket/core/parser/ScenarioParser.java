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

import io.sailrocket.core.builders.PhaseBuilder;
import io.sailrocket.core.builders.ScenarioBuilder;

import org.yaml.snakeyaml.events.AliasEvent;
import org.yaml.snakeyaml.events.Event;
import org.yaml.snakeyaml.events.MappingEndEvent;
import org.yaml.snakeyaml.events.MappingStartEvent;

class ScenarioParser extends AbstractParser<PhaseBuilder, ScenarioBuilder> {

    ScenarioParser() {
        this.subBuilders.put("initialSequences", new SequenceParser(ScenarioBuilder::initialSequence));
        this.subBuilders.put("sequences", new SequenceParser(ScenarioBuilder::sequence));
        this.subBuilders.put("intVars", new VarParser(ScenarioBuilder::intVar));
        this.subBuilders.put("objectVars", new VarParser(ScenarioBuilder::objectVar));
    }

    @Override
    public void parse(Context ctx, PhaseBuilder target) throws ConfigurationParserException {
        if (!ctx.hasNext()) {
            throw ctx.noMoreEvents(MappingStartEvent.class, AliasEvent.class);
        }
        Event event = ctx.next();
        if (event instanceof MappingStartEvent) {
            ScenarioBuilder scenario = target.scenario();
            String anchor = ((MappingStartEvent) event).getAnchor();
            if (anchor != null) {
                ctx.setAnchor(event, anchor, scenario);
            }
            callSubBuilders(ctx, scenario, MappingEndEvent.class);
        } else if (event instanceof AliasEvent){
            String anchor = ((AliasEvent) event).getAnchor();
            Object scenario = ctx.getAnchor(event, anchor);
            if (scenario instanceof ScenarioBuilder) {
                target.scenario().readFrom((ScenarioBuilder) scenario);
            } else {
                throw new ConfigurationParserException("Aliased anchor '" + anchor + "' is not a scenario: " + scenario);
            }
        } else {
            throw ctx.unexpectedEvent(event);
        }
    }
}
