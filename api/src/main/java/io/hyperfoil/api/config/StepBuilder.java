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

import java.util.List;

import io.hyperfoil.function.SerializableSupplier;

/**
 * @author <a href="mailto:stalep@gmail.com">Ståle Pedersen</a>
 */
public interface StepBuilder {
   default void prepareBuild() {}

   List<Step> build(SerializableSupplier<Sequence> sequence);

   BaseSequenceBuilder endStep();

   /**
    * Create a copy of this builder, adding it as a next step to the provided sequence.
    *
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

   interface Factory extends ServiceLoadedFactory<StepBuilder> {}
}
