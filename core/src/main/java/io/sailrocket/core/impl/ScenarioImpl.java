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
package io.sailrocket.core.impl;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.sailrocket.api.config.Scenario;
import io.sailrocket.api.config.Sequence;

public class ScenarioImpl implements Scenario {
    private final Sequence[] initialSequences;
    private final Sequence[] sequences;
    private final String[] objectVars;
    private final String[] intVars;
    private final Map<String, Sequence> sequenceMap;

    public ScenarioImpl(Sequence[] initialSequences, Sequence[] sequences, String[] objectVars, String[] intVars) {
        this.initialSequences = initialSequences;
        this.sequences = sequences;
        this.objectVars = objectVars;
        this.intVars = intVars;
        sequenceMap = Stream.of(sequences).collect(Collectors.toMap(s -> s.name(), Function.identity()));
    }

    @Override
    public Sequence[] initialSequences() {
        return initialSequences;
    }

    @Override
    public Sequence[] sequences() {
        return sequences;
    }

    @Override
    public String[] objectVars() {
       return objectVars;
    }

    @Override
    public String[] intVars() {
       return intVars;
    }

    @Override
    public int maxRequests() {
        // TODO
        return 16;
    }

    @Override
    public int maxSequences() {
        // TODO
        return 16;
    }

    @Override
    public Sequence sequence(String name) {
        Sequence sequence = sequenceMap.get(name);
        if (sequence == null) {
            throw new IllegalArgumentException("Unknown sequence '" + name + "'");
        }
        return sequence;
    }
}

