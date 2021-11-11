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

public class BenchmarkDefinitionException extends RuntimeException {
   public BenchmarkDefinitionException(String msg) {
      super(getMessage(msg));
   }

   public BenchmarkDefinitionException(String msg, Throwable cause) {
      super(getMessage(msg), cause);
   }

   private static String getMessage(String msg) {
      Locator locator = Locator.current();
      String phase = locator.scenario().endScenario().name;
      String sequence = locator.sequence().name();
      String step = null;
      if (locator.step() != null) {
         Class<?> builderClass = locator.step().getClass();
         if (locator.step() instanceof StepBuilder.ActionAdapter) {
            builderClass = ((StepBuilder.ActionAdapter) locator.step()).builder.getClass();
         }
         Name nameAnnotation = builderClass.getAnnotation(Name.class);
         if (nameAnnotation != null) {
            step = nameAnnotation.value();
         } else {
            if ("Builder".equals(builderClass.getSimpleName()) && builderClass.getEnclosingClass() != null) {
               builderClass = builderClass.getEnclosingClass();
            }
            step = builderClass.getSimpleName();
            if (step.endsWith("Step")) {
               step = step.substring(0, step.length() - 4);
            } else if (step.endsWith("Action")) {
               step = step.substring(0, step.length() - 6);
            }
         }
         step = String.format(", step %s (%d/%d)", step, locator.sequence().indexOf(locator.step()), locator.sequence().size());
      }
      return String.format("Phase %s, sequence %s%s: %s", phase, sequence, step != null ? step : "", msg);
   }
}
