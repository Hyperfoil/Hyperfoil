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

/**
 * @author <a href="mailto:stalep@gmail.com">Ståle Pedersen</a>
 */
public class SequenceBuilder extends BaseSequenceBuilder {
   private final ScenarioBuilder scenario;
   private final String name;
   private int id;
   // Concurrency 0 means single instance (not allowing access to sequence-scoped vars)
   // while 1 would be a special case of concurrent instances with only one allowed.
   private int concurrency = 0;
   private Sequence sequence;
   // Next sequence as set by parser. It's not possible to add this as nextSequence step
   // since that would break anchors - we can insert it only after parsing is complete.
   private String nextSequence;

   SequenceBuilder(ScenarioBuilder scenario, String name) {
      super(null);
      this.scenario = scenario;
      int concurrencyIndex = name.indexOf('[');
      this.name = concurrencyIndex < 0 ? name : name.substring(0, concurrencyIndex).trim();
      if (concurrencyIndex >= 0) {
         if (!name.endsWith("]")) {
            throw new BenchmarkDefinitionException("Malformed sequence name with concurrency: " + name);
         }
         try {
            this.concurrency = Integer.parseInt(name.substring(concurrencyIndex + 1, name.length() - 1));
         } catch (NumberFormatException e) {
            throw new BenchmarkDefinitionException("Malformed sequence name with concurrency: " + name);
         }
      }
   }

   SequenceBuilder(ScenarioBuilder scenario, SequenceBuilder other) {
      super(null);
      this.scenario = scenario;
      this.name = other.name;
      this.concurrency = other.concurrency;
      readFrom(other);
      this.nextSequence = other.nextSequence;
   }

   public SequenceBuilder concurrency(int concurrency) {
      this.concurrency = concurrency;
      return this;
   }

   public int concurrency() {
      return concurrency;
   }

   @Override
   public void prepareBuild() {
      Locator.push(createLocator());
      // capture local var to prevent SequenceBuilder serialization
      String nextSequence = this.nextSequence;
      if (nextSequence != null) {
         step(s -> {
            s.nextSequence(nextSequence);
            return true;
         });
      }
      super.prepareBuild();
      Locator.pop();
   }

   public Sequence build(SerializableSupplier<Phase> phase) {
      if (sequence != null) {
         return sequence;
      }
      Locator.push(createLocator());
      sequence = new SequenceImpl(phase, this.name, id, this.concurrency, buildSteps().toArray(new Step[0]));
      Locator.pop();
      return sequence;
   }

   void id(int id) {
      this.id = id;
   }

   @Override
   public SequenceBuilder end() {
      return this;
   }

   public ScenarioBuilder endSequence() {
      return scenario;
   }

   public String name() {
      return name;
   }

   public void nextSequence(String nextSequence) {
      this.nextSequence = nextSequence;
   }
}
