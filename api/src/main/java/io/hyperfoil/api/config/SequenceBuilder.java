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

import io.hyperfoil.api.session.Session;

/**
 * @author <a href="mailto:stalep@gmail.com">Ståle Pedersen</a>
 */
public class SequenceBuilder extends BaseSequenceBuilder<SequenceBuilder> {
   @IgnoreCopy
   private final ScenarioBuilder scenario;
   private String name;
   private int id;
   // Concurrency 0 means single instance (not allowing access to sequence-scoped vars)
   // while 1 would be a special case of concurrent instances with only one allowed.
   private int concurrency = 0;
   private Sequence sequence;
   // Next sequence as set by parser. It's not possible to add this as nextSequence step
   // since that would break anchors - we can insert it only after parsing is complete.
   private String nextSequence;

   // this method is public for copy()
   public SequenceBuilder(ScenarioBuilder scenario) {
      super(null);
      this.scenario = scenario;
   }

   SequenceBuilder name(String name) {
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
      return this;
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
      // capture local var to prevent SequenceBuilder serialization
      String nextSequence = this.nextSequence;
      if (nextSequence != null) {
         step(new NextSequenceStep(nextSequence));
      }
      super.prepareBuild();
   }

   public Sequence build(int offset) {
      if (sequence != null) {
         return sequence;
      }
      sequence = new Sequence(this.name, id, this.concurrency, offset, buildSteps().toArray(new Step[0]));
      return sequence;
   }

   void id(int id) {
      this.id = id;
   }

   @Override
   public SequenceBuilder rootSequence() {
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

   private static class NextSequenceStep implements Step {
      private final String sequence;

      NextSequenceStep(String sequence) {
         this.sequence = sequence;
      }

      @Override
      public boolean invoke(Session s) {
         s.startSequence(sequence, false, Session.ConcurrencyPolicy.FAIL);
         return true;
      }
   }
}
