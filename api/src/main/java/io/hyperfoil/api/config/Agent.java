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
import java.util.Collections;
import java.util.Map;

public class Agent implements Serializable {
   private static final String THREADS = "threads";

   public final String name;
   public final String inlineConfig;
   public final Map<String, String> properties;

   public Agent(String name, String inlineConfig, Map<String, String> properties) {
      this.name = name;
      this.inlineConfig = inlineConfig;
      this.properties = properties == null ? Collections.emptyMap() : properties;
   }

   public int threads() {
      String threadsProperty = properties.get(THREADS);
      if (threadsProperty == null || threadsProperty.isEmpty()) {
         return 0;
      }
      try {
         return Integer.parseInt(threadsProperty);
      } catch (NumberFormatException e) {
         throw new BenchmarkDefinitionException("Cannot parse number of threads for agent " + name + ": " + threadsProperty);
      }
   }
}
