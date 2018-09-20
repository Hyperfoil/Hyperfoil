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

import io.sailrocket.core.builders.BenchmarkBuilder;

import java.util.Iterator;

import org.yaml.snakeyaml.events.Event;
import org.yaml.snakeyaml.events.MappingEndEvent;
import org.yaml.snakeyaml.events.MappingStartEvent;
import org.yaml.snakeyaml.events.ScalarEvent;

class AgentsParser extends BaseParser<BenchmarkBuilder> {

    @Override
    public void parse(Iterator<Event> events, BenchmarkBuilder builder) throws ConfigurationParserException {
        expectEvent(events, MappingStartEvent.class);
        while (events.hasNext()) {
            Event next = events.next();
            if (next instanceof MappingEndEvent) {
                break;
            } else if (next instanceof ScalarEvent) {
                String name = ((ScalarEvent) next).getValue();
                ScalarEvent event = expectEvent(events, ScalarEvent.class);
                builder.addAgent(name, event.getValue());
            } else {
                throw unexpectedEvent(next);
            }
        }
    }

}
