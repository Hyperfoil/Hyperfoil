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

import io.sailrocket.core.builders.SLABuilder;

import java.util.Iterator;

import org.yaml.snakeyaml.events.Event;
import org.yaml.snakeyaml.events.MappingEndEvent;
import org.yaml.snakeyaml.events.MappingStartEvent;

class SLAParser extends AbstractParser<SLABuilder, SLABuilder> {
    private static final SLAParser INSTANCE = new SLAParser();

    SLAParser() {
        subBuilders.put("window", new PropertyParser.String<>(SLABuilder::window));
        subBuilders.put("errorRate", new PropertyParser.Double<>(SLABuilder::errorRate));
        subBuilders.put("meanResponseTime", new PropertyParser.String<>(SLABuilder::meanResponseTime));
        subBuilders.put("limits", new PercentileLimitsParser());
    }

    public static SLAParser instance(){
        return INSTANCE;
    }

    @Override
    public void parse(Iterator<Event> events, SLABuilder target) throws ConfigurationParserException {
        expectEvent(events, MappingStartEvent.class);
        callSubBuilders(events, target, MappingEndEvent.class);
    }
}

