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

/**
 * @author <a href="mailto:stalep@gmail.com">Ståle Pedersen</a>
 */
@IncludeBuilders(
      @IncludeBuilders.Conversion(from = Action.Builder.class, adapter = StepBuilder.ActionBuilderConverter.class)
)
public interface StepBuilder<S extends StepBuilder<S>> extends BuilderBase<S> {

   List<Step> build();

   default int id() {
      return -1;
   }

   class ActionBuilderConverter implements Function<Action.Builder, StepBuilder> {
      @Override
      public StepBuilder apply(Action.Builder builder) {
         return new ActionAdapter(builder);
      }
   }

   class ActionAdapter implements StepBuilder<ActionAdapter> {
      private Action.Builder builder;

      public ActionAdapter(Action.Builder builder) {
         this.builder = builder;
      }

      @Override
      public void prepareBuild() {
         builder.prepareBuild();
      }

      @Override
      public ActionAdapter copy(Locator locator) {
         ActionAdapter copy = new ActionAdapter(null);
         Action.Builder bc = builder.copy(Locator.get(copy, locator));
         copy.builder = bc;
         return copy;
      }

      @Override
      public List<Step> build() {
         return Collections.singletonList(new ActionStep(builder.build()));
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
