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

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import io.hyperfoil.api.session.Action;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.function.SerializableSupplier;

/**
 * @author <a href="mailto:stalep@gmail.com">Ståle Pedersen</a>
 */
@IncludeBuilders(
      @IncludeBuilders.Conversion(from = Action.Builder.class, adapter = StepBuilder.ActionBuilderConverter.class)
)
public interface StepBuilder {

   default void prepareBuild() {}

   List<Step> build(SerializableSupplier<Sequence> sequence);

   BaseSequenceBuilder endStep();

   /**
    * Create a copy of this builder, adding it as a next step to the provided sequence.
    * <p>
    * If this builder does not use its position (calling {@link #endStep()} either directly or
    * e.g. through {@link Locator#fromStep(StepBuilder)}) it can just add <code>this</code>
    * without doing actual copy.
    *
    * @param newParent New parent sequence.
    */
   default void addCopyTo(BaseSequenceBuilder newParent) {
      if (canBeLocated()) {
         throw new IllegalStateException("This default method cannot be used on " + getClass().getName());
      }
      newParent.stepBuilder(this);
   }

   /**
    * Override this along with {@link #addCopyTo(BaseSequenceBuilder)}.
    *
    * @return True if the object supports deep copy.
    */
   default boolean canBeLocated() {
      return false;
   }

   class ActionBuilderConverter implements Function<Action.Builder, StepBuilder> {
      @Override
      public StepBuilder apply(Action.Builder builder) {
         return new ActionAdapter(builder);
      }
   }

   class ActionAdapter implements StepBuilder {
      private final Action.Builder builder;

      public ActionAdapter(Action.Builder builder) {
         this.builder = builder;
      }

      @Override
      public void prepareBuild() {
         builder.prepareBuild();
      }

      @Override
      public void addCopyTo(BaseSequenceBuilder newParent) {
         Action.Builder copy = builder.copy(newParent.createLocator());
         newParent.stepBuilder(new ActionAdapter(copy));
      }

      @Override
      public boolean canBeLocated() {
         return true;
      }

      @Override
      public List<Step> build(SerializableSupplier<Sequence> sequence) {
         return Collections.singletonList(new ActionStep(builder.build()));
      }

      @Override
      public BaseSequenceBuilder endStep() {
         throw new UnsupportedOperationException();
      }
   }

   class ActionStep implements Step, ResourceUtilizer {
      private final Action action;

      public ActionStep(Action action) {
         this.action = action;
      }

      @Override
      public boolean invoke(Session session) {
         action.run(session);
         return true;
      }

      @Override
      public void reserve(Session session) {
         ResourceUtilizer.reserve(session, action);
      }
   }
}
