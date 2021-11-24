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
import io.hyperfoil.api.session.Session;

/**
 * @author <a href="mailto:stalep@gmail.com">Ståle Pedersen</a>
 */
@IncludeBuilders(
      @IncludeBuilders.Conversion(from = Action.Builder.class, adapter = StepBuilder.ActionBuilderConverter.class)
)
public interface StepBuilder<S extends StepBuilder<S>> extends BuilderBase<S> {

   static String nameOf(StepBuilder<?> builder) {
      Class<?> builderClass = builder.getClass();
      if (builder instanceof ActionAdapter) {
         builderClass = ((ActionAdapter) builder).builder.getClass();
      }
      Name nameAnnotation = builderClass.getAnnotation(Name.class);
      if (nameAnnotation != null) {
         return nameAnnotation.value();
      } else {
         if ("Builder".equals(builderClass.getSimpleName()) && builderClass.getEnclosingClass() != null) {
            return simpleName(builderClass.getEnclosingClass());
         } else {
            return simpleName(builderClass);
         }
      }
   }

   static String nameOf(Step step) {
      if (step == null) {
         return null;
      }
      Object instance = step;
      if (step instanceof ActionStep) {
         instance = ((ActionStep) step).action;
      }
      Class<?> instanceClass = instance.getClass();
      for (Class<?> maybeBuilder : instanceClass.getClasses()) {
         if ("Builder".equals(maybeBuilder.getSimpleName()) && maybeBuilder.isAnnotationPresent(Name.class)) {
            return maybeBuilder.getAnnotation(Name.class).value();
         }
      }
      return simpleName(instanceClass);
   }

   private static String simpleName(Class<?> builderClass) {
      String name = builderClass.getSimpleName();
      name = Character.toLowerCase(name.charAt(0)) + name.substring(1);
      if (name.endsWith("Step")) {
         return name.substring(0, name.length() - 4);
      } else if (name.endsWith("Action")) {
         return name.substring(0, name.length() - 6);
      } else {
         return name;
      }
   }

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
      final Action.Builder builder;

      public ActionAdapter(Action.Builder builder) {
         this.builder = builder;
      }

      @Override
      public void prepareBuild() {
         builder.prepareBuild();
      }

      @Override
      public ActionAdapter copy(Object newParent) {
         return new ActionAdapter(builder.copy(null));
      }

      @Override
      public List<Step> build() {
         return Collections.singletonList(new ActionStep(builder.build()));
      }
   }

   class ActionStep implements Step {
      private final Action action;

      public ActionStep(Action action) {
         this.action = action;
      }

      @Override
      public boolean invoke(Session session) {
         action.run(session);
         return true;
      }
   }
}
