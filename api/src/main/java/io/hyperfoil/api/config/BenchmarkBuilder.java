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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import io.hyperfoil.impl.FutureSupplier;

/**
 * @author <a href="mailto:stalep@gmail.com">Ståle Pedersen</a>
 */
public class BenchmarkBuilder {

   private final String originalSource;
   private final BenchmarkData data;
   private String name;
   private Collection<Agent> agents = new ArrayList<>();
   private ErgonomicsBuilder ergonomics = new ErgonomicsBuilder();
   private HttpBuilder defaultHttp;
   private List<HttpBuilder> httpList = new ArrayList<>();
   private int threads = 1;
   private Map<String, PhaseBuilder<?>> phaseBuilders = new HashMap<>();
   private long statisticsCollectionPeriod = 1000;
   private List<RunHook> preHooks = new ArrayList<>();
   private List<RunHook> postHooks = new ArrayList<>();

   public BenchmarkBuilder(String originalSource, BenchmarkData data) {
      this.originalSource = originalSource;
      this.data = data;
   }

   public static BenchmarkBuilder builder() {
      return new BenchmarkBuilder(null, BenchmarkData.EMPTY);
   }

   public BenchmarkBuilder name(String name) {
      this.name = name;
      return this;
   }

   public BenchmarkBuilder addAgent(String name, String inlineConfig, Map<String, String> properties) {
      Agent agent = new Agent(name, inlineConfig, properties);
      if (agents.stream().anyMatch(a -> a.name.equals(agent.name))) {
         throw new BenchmarkDefinitionException("Benchmark already contains agent '" + agent.name + "'");
      }
      agents.add(agent);
      return this;
   }

   int numAgents() {
      return agents.size();
   }

   public ErgonomicsBuilder ergonomics() {
      return ergonomics;
   }

   public HttpBuilder http() {
      if (defaultHttp == null) {
         defaultHttp = new HttpBuilder(this);
      }
      return defaultHttp;
   }

   public HttpBuilder http(String host) {
      HttpBuilder builder = new HttpBuilder(this).host(host);
      httpList.add(builder);
      return builder;
   }

   public BenchmarkBuilder threads(int threads) {
      this.threads = threads;
      return this;
   }

   public PhaseBuilder.Catalog addPhase(String name) {
      return new PhaseBuilder.Catalog(this, name);
   }

   public PhaseBuilder.ConstantPerSec singleConstantPerSecPhase() {
      if (phaseBuilders.isEmpty()) {
         return new PhaseBuilder.Catalog(this, "main").constantPerSec(0);
      }
      PhaseBuilder<?> builder = phaseBuilders.get("main");
      if (!(builder instanceof PhaseBuilder.ConstantPerSec)) {
         throw new BenchmarkDefinitionException("Benchmark already has defined phases; cannot use single-phase definition");
      }
      return (PhaseBuilder.ConstantPerSec) builder;
   }

   public BenchmarkBuilder addPreHook(RunHook runHook) {
      preHooks.add(runHook);
      return this;
   }

   public BenchmarkBuilder addPostHook(RunHook runHook) {
      postHooks.add(runHook);
      return this;
   }

   public void prepareBuild() {
      if (defaultHttp == null) {
         if (httpList.isEmpty()) {
            // may be removed in the future when we define more than HTTP connections
            throw new BenchmarkDefinitionException("No default HTTP target set!");
         } else if (httpList.size() == 1) {
            defaultHttp = httpList.iterator().next();
         }
      } else {
         if (httpList.stream().anyMatch(http -> http.authority().equals(defaultHttp.authority()))) {
            throw new BenchmarkDefinitionException("Ambiguous HTTP definition for "
                  + defaultHttp.authority() + ": defined both as default and non-default");
         }
         httpList.add(defaultHttp);
      }
      HashSet<String> authorities = new HashSet<>();
      for (HttpBuilder http : httpList) {
         if (!authorities.add(http.authority())) {
            throw new BenchmarkDefinitionException("Duplicit HTTP definition for " + http.authority());
         }
      }
      httpList.forEach(HttpBuilder::prepareBuild);
      phaseBuilders.values().forEach(PhaseBuilder::prepareBuild);
   }

   public Benchmark build() {
      prepareBuild();
      FutureSupplier<Benchmark> bs = new FutureSupplier<>();
      Map<String, Http> httpMap = httpList.stream()
            .collect(Collectors.toMap(HttpBuilder::authority, http -> http.build(http == defaultHttp)));

      AtomicInteger phaseIdCounter = new AtomicInteger(0);
      Collection<Phase> phases = phaseBuilders.values().stream()
            .flatMap(builder -> builder.build(bs, phaseIdCounter).stream()).collect(Collectors.toList());
      Set<String> phaseNames = phases.stream().map(Phase::name).collect(Collectors.toSet());
      for (Phase phase : phases) {
         checkDependencies(phase, phase.startAfter, phaseNames);
         checkDependencies(phase, phase.startAfterStrict, phaseNames);
         checkDependencies(phase, phase.terminateAfterStrict, phaseNames);
      }
      Map<String, Object> tags = new HashMap<>();
      if (defaultHttp != null) {
         Http defaultHttp = this.defaultHttp.build(true);
         tags.put("url", defaultHttp.protocol().scheme + "://" + defaultHttp.host() + ":" + defaultHttp.port());
         tags.put("protocol", defaultHttp.protocol().scheme);
      }
      tags.put("threads", threads);

      // It is important to gather files only after all other potentially file-reading builders
      // are done.
      Map<String, byte[]> files = data.files();

      Benchmark benchmark = new Benchmark(name, originalSource, files, agents.toArray(new Agent[0]), threads, ergonomics.build(),
            httpMap, phases, tags, statisticsCollectionPeriod, preHooks, postHooks);
      bs.set(benchmark);
      return benchmark;
   }

   private void checkDependencies(Phase phase, Collection<String> references, Set<String> phaseNames) {
      for (String dep : references) {
         if (!phaseNames.contains(dep)) {
            String suggestion = phaseNames.stream()
                  .filter(name -> name.toLowerCase().startsWith(dep)).findAny()
                  .map(name -> " Did you mean " + name + "?").orElse("");
            throw new BenchmarkDefinitionException("Phase " + dep + " referenced from " + phase.name() + " is not defined." + suggestion);
         }
      }
   }

   void addPhase(String name, PhaseBuilder phaseBuilder) {
      if (phaseBuilders.containsKey(name)) {
         throw new IllegalArgumentException("Phase '" + name + "' already defined.");
      }
      phaseBuilders.put(name, phaseBuilder);
   }

   public BenchmarkBuilder statisticsCollectionPeriod(long statisticsCollectionPeriod) {
      this.statisticsCollectionPeriod = statisticsCollectionPeriod;
      return this;
   }

   Collection<PhaseBuilder<?>> phases() {
      return phaseBuilders.values();
   }

   public boolean validateAuthority(String authority) {
      return authority == null && defaultHttp != null || httpList.stream().anyMatch(http -> http.authority().equals(authority));
   }

   public HttpBuilder decoupledHttp() {
      return new HttpBuilder(this);
   }

   public void addHttp(HttpBuilder builder) {
      if (builder.authority() == null) {
         throw new BenchmarkDefinitionException("Missing hostname!");
      }
      httpList.add(builder);
   }

   public BenchmarkData data() {
      return data;
   }
}
