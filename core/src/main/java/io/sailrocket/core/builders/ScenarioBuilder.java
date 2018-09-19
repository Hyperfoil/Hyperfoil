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

import io.sailrocket.api.config.BenchmarkDefinitionException;
import io.sailrocket.api.config.Sequence;
import io.sailrocket.api.config.Scenario;

import java.util.ArrayList;
import java.util.Collection;

/**
 * @author <a href="mailto:stalep@gmail.com">Ståle Pedersen</a>
 */
public class ScenarioBuilder {

    private final PhaseBuilder<?> phaseBuilder;
    private Collection<SequenceBuilder> initialSequences = new ArrayList<>();
    private Collection<SequenceBuilder> sequences = new ArrayList<>();
    private Collection<String> objectVars = new ArrayList<>();
    private Collection<String> intVars = new ArrayList<>();
    private Scenario scenario;

    ScenarioBuilder(PhaseBuilder<?> phaseBuilder) {
        this.phaseBuilder = phaseBuilder;
    }

    public PhaseBuilder endScenario() {
        return phaseBuilder;
    }

    ScenarioBuilder initialSequence(SequenceBuilder sequence) {
        initialSequences.add(sequence);
        sequence.id(sequences.size());
        sequences.add(sequence);
        return this;
    }

    public SequenceBuilder initialSequence(String name) {
        SequenceBuilder builder = new SequenceBuilder(this, name);
        initialSequence(builder);
        return builder;
    }

    ScenarioBuilder sequence(SequenceBuilder sequence) {
        sequence.id(sequences.size());
        sequences.add(sequence);
        return this;
    }

    public SequenceBuilder sequence(String name) {
        SequenceBuilder builder = new SequenceBuilder(this, name);
        sequence(builder);
        return builder;
    }

    public SequenceBuilder findSequence(String name) {
        return sequences.stream().filter(sb -> name.equals(sb.name())).findFirst()
              .orElseThrow(() -> new BenchmarkDefinitionException("No sequence " + name + " in phase " + endScenario().name));
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
        return scenario = new Scenario(
              initialSequences.stream().map(SequenceBuilder::build).toArray(Sequence[]::new),
              sequences.stream().map(SequenceBuilder::build).toArray(Sequence[]::new),
              objectVars.toArray(new String[0]),
              intVars.toArray(new String[0]));
    }

}
