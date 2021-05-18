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
package io.hyperfoil.api.config;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.hyperfoil.api.session.AccessVisitor;
import io.hyperfoil.api.session.ReadAccess;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.session.WriteAccess;

public class Scenario implements Serializable {
   private final Sequence[] initialSequences;
   private final Sequence[] sequences;
   private final Map<String, Sequence> sequenceMap;
   private final int maxRequests;
   private final int maxSequences;
   private final int sumConcurrency;
   private final WriteAccess[] writes;
   private final int uniqueVars;

   public Scenario(Sequence[] initialSequences, Sequence[] sequences, int maxRequests, int maxSequences) {
      this.initialSequences = initialSequences;
      this.sequences = sequences;
      this.maxRequests = maxRequests;
      this.maxSequences = maxSequences;
      sequenceMap = Stream.of(sequences).collect(Collectors.toMap(Sequence::name, Function.identity()));
      sumConcurrency = sequenceMap.values().stream().mapToInt(Sequence::concurrency).sum();
      AccessVisitor visitor = new AccessVisitor();
      visitor.visit(sequences);
      writes = visitor.writes();
      ReadAccess[] reads = visitor.reads();
      Set<Object> writtenKeys = Arrays.stream(writes).map(ReadAccess::key).collect(Collectors.toSet());
      Set<Object> readKeys = Arrays.stream(reads).map(ReadAccess::key).filter(Objects::nonNull).collect(Collectors.toSet());
      readKeys.forEach(key -> {
         if (!writtenKeys.contains(key)) {
            // TODO: calculate similar variable names using Levenshtein distance and hint
            throw new BenchmarkDefinitionException("Variable '" + key + "' is read but it is never written to.");
         }
      });
      Map<Object, Integer> keyIndexMap = new HashMap<>();
      for (Object key : readKeys) {
         keyIndexMap.put(key, keyIndexMap.size());
      }
      uniqueVars = keyIndexMap.size();
      for (ReadAccess access : reads) {
         if (access.key() != null) {
            access.setIndex(keyIndexMap.get(access.key()));
         }
      }
   }

   public Sequence[] initialSequences() {
      return initialSequences;
   }

   public Sequence[] sequences() {
      return sequences;
   }

   public int maxRequests() {
      return maxRequests;
   }

   public int maxSequences() {
      return maxSequences;
   }

   public int sumConcurrency() {
      return sumConcurrency;
   }

   public Sequence sequence(String name) {
      Sequence sequence = sequenceMap.get(name);
      if (sequence == null) {
         throw new IllegalArgumentException("Unknown sequence '" + name + "'");
      }
      return sequence;
   }

   public Session.Var[] createVars(Session session) {
      Session.Var[] vars = new Session.Var[uniqueVars];
      for (WriteAccess access : writes) {
         int index = access.index();
         vars[index] = access.createVar(session, vars[index]);
      }
      return vars;
   }
}

