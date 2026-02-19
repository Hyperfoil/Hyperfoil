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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author <a href="mailto:stalep@gmail.com">Ståle Pedersen</a>
 */
public class ScenarioBuilder {

   private final PhaseBuilder<?> phaseBuilder;
   private final PhaseForkBuilder forkBuilder;
   private List<SequenceBuilder> initialSequences = new ArrayList<>();
   private List<SequenceBuilder> sequences = new ArrayList<>();
   private Scenario scenario;
   private int maxRequests = 16;
   // We don't use sum of concurrency because that could be excessively high
   private int maxSequences = 16;

   ScenarioBuilder(PhaseBuilder<?> phaseBuilder, PhaseForkBuilder forkBuilder) {
      this.phaseBuilder = phaseBuilder;
      this.forkBuilder = forkBuilder;
   }

   public PhaseForkBuilder fork() {
      return forkBuilder;
   }

   public PhaseBuilder<?> endScenario() {
      return phaseBuilder;
   }

   private void initialSequence(SequenceBuilder sequence) {
      initialSequences.add(sequence);
      sequence(sequence);
   }

   public List<SequenceBuilder> resetInitialSequences() {
      List<SequenceBuilder> prev = this.initialSequences;
      initialSequences = new ArrayList<>();
      return prev;
   }

   public SequenceBuilder initialSequence(String name, SequenceBuilder copyFrom) {
      SequenceBuilder sequenceBuilder = copyFrom == null ? new SequenceBuilder(this) : copyFrom.copy(this);
      sequenceBuilder.nextSequence(null);
      initialSequence(sequenceBuilder.name(name));
      return sequenceBuilder;
   }

   public SequenceBuilder initialSequence(String name) {
      SequenceBuilder builder = new SequenceBuilder(this).name(name);
      initialSequence(builder);
      return builder;
   }

   private void sequence(SequenceBuilder sequence) {
      sequence.id(sequences.size());
      sequences.add(sequence);
   }

   public SequenceBuilder sequence(String name, SequenceBuilder copyFrom) {
      SequenceBuilder sequenceBuilder = copyFrom == null ? new SequenceBuilder(this) : copyFrom.copy(this);
      sequenceBuilder.nextSequence(null);
      sequence(sequenceBuilder.name(name));
      return sequenceBuilder;
   }

   public SequenceBuilder sequence(String name) {
      SequenceBuilder builder = new SequenceBuilder(this).name(name);
      sequence(builder);
      return builder;
   }

   public boolean hasSequence(String name) {
      return sequences.stream().anyMatch(sb -> name.equals(sb.name()));
   }

   public SequenceBuilder findSequence(String name) {
      return sequences.stream().filter(sb -> name.equals(sb.name())).findFirst()
            .orElseThrow(() -> new BenchmarkDefinitionException("No sequence " + name + " in phase " + endScenario().name()));
   }

   public ScenarioBuilder maxRequests(int maxRequests) {
      this.maxRequests = maxRequests;
      return this;
   }

   public ScenarioBuilder maxSequences(int maxSequences) {
      this.maxSequences = maxSequences;
      return this;
   }

   public boolean hasOpenModelPhase() {
      return phaseBuilder instanceof PhaseBuilder.OpenModel<?>;
   }

   public boolean isRootSequence(BaseSequenceBuilder<?> sequence) {
      if (!(sequence instanceof SequenceBuilder seq)) {
         // this includes all impl of BaseSequenceBuilder that are not SequenceBuilder e.g. loop, etc
         return false;
      }
      if (!sequences.contains(seq)) {
         throw new IllegalStateException("Sequence " + sequence.name() + " is not part of the scenario!");
      }
      return initialSequences.contains(sequence);
   }

   public void prepareBuild() {
      new ArrayList<>(sequences).forEach(SequenceBuilder::prepareBuild);
   }

   public Scenario build() {
      Locator.push(this);
      try {
         if (scenario != null) {
            return scenario;
         }
         if (initialSequences.isEmpty()) {
            throw new BenchmarkDefinitionException("No initial sequences in phase " + endScenario().name());
         }

         Sequence[] initialSequences = new Sequence[this.initialSequences.size()];
         int offset = 0;
         for (int i = 0; i < this.initialSequences.size(); i++) {
            Sequence sequence = this.initialSequences.get(i).build(offset);
            initialSequences[i] = sequence;
            offset += sequence.concurrency() > 0 ? sequence.concurrency() : 1;
         }
         Sequence[] sequences = new Sequence[this.sequences.size()];
         for (int i = 0; i < this.sequences.size(); i++) {
            Sequence sequence = this.sequences.get(i).build(offset);
            sequences[i] = sequence;
            offset += sequence.concurrency() > 0 ? sequence.concurrency() : 1;
         }

         int maxSequences = Math.max(Stream.of(sequences).mapToInt(sequence -> {
            boolean isInitial = Stream.of(initialSequences).anyMatch(s -> s == sequence);
            return isInitial ? sequence.concurrency() : sequence.concurrency() + 1;
         }).max().orElse(1), this.maxSequences);
         return scenario = new Scenario(
               initialSequences,
               sequences,
               maxRequests,
               maxSequences);
      } finally {
         Locator.pop();
      }
   }

   public void readFrom(ScenarioBuilder other) {
      this.sequences = other.sequences.stream()
            .map(seq -> seq.copy(this)).collect(Collectors.toList());
      this.initialSequences = other.initialSequences.stream()
            .map(seq -> findMatchingSequence(seq.name())).collect(Collectors.toList());
   }

   private SequenceBuilder findMatchingSequence(String name) {
      return this.sequences.stream().filter(s2 -> s2.name().equals(name)).findFirst().orElseThrow(IllegalStateException::new);
   }
}
