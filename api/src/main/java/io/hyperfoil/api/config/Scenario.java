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
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Scenario implements Serializable {
   private final Sequence[] initialSequences;
   private final Sequence[] sequences;
   private final String[] objectVars;
   private final String[] intVars;
   private final Map<String, Sequence> sequenceMap;
   private int maxRequests;
   private int maxSequences;

   public Scenario(Sequence[] initialSequences, Sequence[] sequences, String[] objectVars, String[] intVars, int maxRequests, int maxSequences) {
      this.initialSequences = initialSequences;
      this.sequences = sequences;
      this.objectVars = objectVars;
      this.intVars = intVars;
      this.maxRequests = maxRequests;
      this.maxSequences = maxSequences;
      sequenceMap = Stream.of(sequences).collect(Collectors.toMap(s -> s.name(), Function.identity()));
   }

   public Sequence[] initialSequences() {
      return initialSequences;
   }

   public Sequence[] sequences() {
      return sequences;
   }

   public String[] objectVars() {
      return objectVars;
   }

   public String[] intVars() {
      return intVars;
   }

   public int maxRequests() {
      return maxRequests;
   }

   public int maxSequences() {
      return maxSequences;
   }

   public Sequence sequence(String name) {
      Sequence sequence = sequenceMap.get(name);
      if (sequence == null) {
         throw new IllegalArgumentException("Unknown sequence '" + name + "'");
      }
      return sequence;
   }
}

