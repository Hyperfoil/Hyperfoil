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
 *
 */

package io.sailrocket.core.builders;

import io.sailrocket.api.Scenario;
import io.sailrocket.api.Sequence;
import io.sailrocket.core.impl.ScenarioImpl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Consumer;

/**
 * @author <a href="mailto:stalep@gmail.com">Ståle Pedersen</a>
 */
public class ScenarioBuilder {

    private Collection<SequenceBuilder> initialSequences = new ArrayList<>();
    private Collection<SequenceBuilder> sequences = new ArrayList<>();
    private Collection<String> objectVars = new ArrayList<>();
    private Collection<String> intVars = new ArrayList<>();
    private Scenario scenario;

    private ScenarioBuilder() {
    }

    public static ScenarioBuilder scenarioBuilder() {
        return new ScenarioBuilder();
    }

    private ScenarioBuilder apply(Consumer<ScenarioBuilder> consumer) {
        assert scenario == null;
        consumer.accept(this);
        return this;
    }

    public ScenarioBuilder initialSequence(SequenceBuilder sequence) {
        return apply(clone -> {
            clone.initialSequences.add(sequence);
            sequence.id(clone.sequences.size());
            clone.sequences.add(sequence);
        });
    }

    public ScenarioBuilder sequence(SequenceBuilder sequence) {
        return apply(clone -> {
            sequence.id(clone.sequences.size());
            clone.sequences.add(sequence);
        });
    }

    public ScenarioBuilder objectVar(String var) {
        assert scenario == null;
        objectVars.add(var);
        return this;
    }

    public ScenarioBuilder intVar(String var) {
        assert scenario == null;
        intVars.add(var);
        return this;
    }

    public Scenario build() {
        if (scenario != null) {
            return scenario;
        }
        if (initialSequences.isEmpty()) {
            throw new IllegalArgumentException("No initial sequences.");
        }
        return scenario = new ScenarioImpl(
              initialSequences.stream().map(SequenceBuilder::build).toArray(Sequence[]::new),
              sequences.stream().map(SequenceBuilder::build).toArray(Sequence[]::new),
              objectVars.toArray(new String[0]),
              intVars.toArray(new String[0]));
    }

}
