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

import java.util.HashMap;
import java.util.Map;

import org.yaml.snakeyaml.events.ScalarEvent;

abstract class AbstractParser<T, S> implements Parser<T> {
    Map<String, Parser<S>> subBuilders = new HashMap<>();

    void callSubBuilders(Context ctx, S target) throws ParserException {
        ctx.parseMapping(target, this::getSubBuilder);
    }

    private Parser<S> getSubBuilder(ScalarEvent event) throws ParserException {
        Parser<S> builder = subBuilders.get(event.getValue());
        if (builder == null) {
            throw new ParserException(event, "Invalid configuration label: '" + event.getValue() + "', expected one of " + subBuilders.keySet());
        }
        return builder;
    }

    protected void register(String property, Parser<S> parser) {
        Parser<S> prev = subBuilders.put(property, parser);
        assert prev == null;
    }

}
