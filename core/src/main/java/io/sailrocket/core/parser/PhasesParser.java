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


import org.yaml.snakeyaml.events.MappingEndEvent;
import org.yaml.snakeyaml.events.MappingStartEvent;
import org.yaml.snakeyaml.events.ScalarEvent;

import io.sailrocket.core.builders.PhaseBuilder;
import io.sailrocket.core.builders.SimulationBuilder;

class PhasesParser extends AbstractParser<SimulationBuilder, PhaseBuilder.Discriminator> {

    PhasesParser() {
        register("!atOnce", new PhaseParser.AtOnce());
        register("!always", new PhaseParser.Always());
        register("!rampPerSec", new PhaseParser.RampPerSec());
        register("!constantPerSec", new PhaseParser.ConstantPerSec());
    }

    @Override
    public void parse(Context ctx, SimulationBuilder target) throws ParserException {
        ctx.parseList(target, this::parsePhase);
    }

    private void parsePhase(Context ctx, SimulationBuilder target) throws ParserException {
        ctx.expectEvent(MappingStartEvent.class);
        ScalarEvent event = ctx.expectEvent(ScalarEvent.class);
        if (event.getTag() == null) {
            throw new ParserException(event, "Phases must be tagged by the type; use one of: " + subBuilders.keySet());
        }
        Parser<PhaseBuilder.Discriminator> phaseBuilder = subBuilders.get(event.getTag());
        if (phaseBuilder == null) {
            throw new ParserException(event, "Unknown phase type: " + event.getTag() + ", expected one of " + subBuilders.keySet());
        }
        String name = event.getValue();
        phaseBuilder.parse(ctx, target.addPhase(name));
        ctx.expectEvent(MappingEndEvent.class);
    }
}
