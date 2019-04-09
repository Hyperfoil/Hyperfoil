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

package io.hyperfoil.api.config;

import io.hyperfoil.function.SerializableSupplier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * @author <a href="mailto:stalep@gmail.com">Ståle Pedersen</a>
 */
public class ScenarioBuilder implements Rewritable<ScenarioBuilder> {

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
              .orElseThrow(() -> new BenchmarkDefinitionException("No sequence " + name + " in phase " + endScenario().name()));
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

    public void prepareBuild() {
        new ArrayList<>(sequences).forEach(SequenceBuilder::prepareBuild);
    }

    public Scenario build(SerializableSupplier<Phase> phase) {
        if (scenario != null) {
            return scenario;
        }
        if (initialSequences.isEmpty()) {
            throw new BenchmarkDefinitionException("No initial sequences in phase " + endScenario().name());
        }
        return scenario = new Scenario(
              initialSequences.stream().map(sequenceBuilder -> sequenceBuilder.build(phase)).toArray(Sequence[]::new),
              sequences.stream().map(sequenceBuilder1 -> sequenceBuilder1.build(phase)).toArray(Sequence[]::new),
              objectVars.toArray(new String[0]),
              intVars.toArray(new String[0]));
    }

    @Override
    public void readFrom(ScenarioBuilder other) {
        this.sequences = other.sequences.stream()
              .map(seq -> new SequenceBuilder(this, seq)).collect(Collectors.toList());
        this.initialSequences = other.initialSequences.stream()
              .map(seq -> findMatchingSequence(seq.name())).collect(Collectors.toList());
        this.intVars = other.intVars;
        this.objectVars = other.objectVars;
    }

    private SequenceBuilder findMatchingSequence(String name) {
        return this.sequences.stream().filter(s2 -> s2.name().equals(name)).findFirst().orElseThrow(IllegalStateException::new);
    }

    Collection<SequenceBuilder> sequences() {
        return sequences;
    }
}
