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

import io.hyperfoil.api.config.BenchmarkBuilder;

import org.yaml.snakeyaml.events.Event;
import org.yaml.snakeyaml.events.MappingEndEvent;
import org.yaml.snakeyaml.events.MappingStartEvent;
import org.yaml.snakeyaml.events.ScalarEvent;
import org.yaml.snakeyaml.events.SequenceStartEvent;

class AgentsParser implements Parser<BenchmarkBuilder> {

    @Override
    public void parse(Context ctx, BenchmarkBuilder builder) throws ParserException {
        Event event = ctx.next();
        if (event instanceof ScalarEvent) {
            String value = ((ScalarEvent) event).getValue();
            if (value == null || value.isEmpty()) {
                // `hosts:` without a value should be equal to omitting agents declaration completely
                return;
            }
        } else if (event instanceof SequenceStartEvent) {
            throw new ParserException(event, "Agent hosts should user properties, not a sequence; each agent name must be unique.");
        } else if (!(event instanceof MappingStartEvent)) {
            throw ctx.unexpectedEvent(event);
        }
        while (ctx.hasNext()) {
            Event next = ctx.next();
            if (next instanceof MappingEndEvent) {
                break;
            } else if (next instanceof ScalarEvent) {
                String name = ((ScalarEvent) next).getValue();
                String hostPort = ctx.expectEvent(ScalarEvent.class).getValue();
                builder.addAgent(name, hostPort);
            } else {
                throw ctx.unexpectedEvent(next);
            }
        }
    }

}
