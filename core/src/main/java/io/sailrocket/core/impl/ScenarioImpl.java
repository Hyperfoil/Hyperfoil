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

import io.sailrocket.api.Scenario;
import io.sailrocket.api.Sequence;

import java.util.ArrayList;
import java.util.List;

public class ScenarioImpl implements Scenario {

    private List<Sequence> sequences = new ArrayList<>();

    @Override
    public Scenario sequence(Sequence sequence) {
        sequences.add(sequence);
        return this;
    }

    @Override
    public Sequence firstSequence() {
        return sequences.get(0);
    }

    @Override
    public List<Sequence> sequences() {
        return sequences;
    }
}
