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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.hyperfoil.impl.FutureSupplier;

/**
 * @author <a href="mailto:stalep@gmail.com">Ståle Pedersen</a>
 */
public class BenchmarkBuilder {

   private final String originalSource;
   private final BenchmarkData data;
   private String name;
   private Map<String, String> defaultAgentProperties = Collections.emptyMap();
   private Collection<Agent> agents = new ArrayList<>();
   private ErgonomicsBuilder ergonomics = new ErgonomicsBuilder(this);
   private HttpBuilder defaultHttp;
   private List<HttpBuilder> httpList = new ArrayList<>();
   private int threads = 1;
   private Map<String, PhaseBuilder<?>> phaseBuilders = new HashMap<>();
   private long statisticsCollectionPeriod = 1000;
   private String triggerUrl;
   private List<RunHook> preHooks = new ArrayList<>();
   private List<RunHook> postHooks = new ArrayList<>();

   public static Collection<PhaseBuilder<?>> phasesForTesting(BenchmarkBuilder builder) {
      return builder.phaseBuilders.values();
   }

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

   public PhaseBuilder.ConstantRate singleConstantRatePhase() {
      if (phaseBuilders.isEmpty()) {
         return new PhaseBuilder.Catalog(this, "main").constantRate(0);
      }
      PhaseBuilder<?> builder = phaseBuilders.get("main");
      if (!(builder instanceof PhaseBuilder.ConstantRate)) {
         throw new BenchmarkDefinitionException("Benchmark already has defined phases; cannot use single-phase definition");
      }
      return (PhaseBuilder.ConstantRate) builder;
   }

   public BenchmarkBuilder triggerUrl(String url) {
      this.triggerUrl = url;
      return this;
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
      Map<String, Phase> phases = phaseBuilders.values().stream()
            .flatMap(builder -> builder.build(bs, phaseIdCounter).stream()).collect(Collectors.toMap(Phase::name, p -> p));
      for (Phase phase : phases.values()) {
         checkDependencies(phase, phase.startAfter, phases);
         checkDependencies(phase, phase.startAfterStrict, phases);
         checkDependencies(phase, phase.terminateAfterStrict, phases);
         checkDeadlock(phase, phases);
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

      Agent[] agents = this.agents.stream().map(a -> {
         HashMap<String, String> properties = new HashMap<>(defaultAgentProperties);
         properties.putAll(a.properties);
         return new Agent(a.name, a.inlineConfig, properties);
      }).toArray(Agent[]::new);
      Benchmark benchmark = new Benchmark(name, originalSource, files, agents, threads, ergonomics.build(),
            httpMap, new ArrayList<>(phases.values()), tags, statisticsCollectionPeriod, triggerUrl, preHooks, postHooks);
      bs.set(benchmark);
      return benchmark;
   }

   private void checkDeadlock(Phase phase, Map<String, Phase> phases) {
      // prevent deadlock
      Map<Phase, Phase> deps = new HashMap<>();
      Queue<Phase> toProcess = new ArrayDeque<>();
      toProcess.add(phase);
      while (!toProcess.isEmpty()) {
         Phase p = toProcess.poll();
         Stream.concat(p.startAfter.stream(), p.startAfterStrict.stream()).forEach(name -> {
            Phase p2 = phases.get(name);
            if (p2 == phase) {
               StringBuilder sb = new StringBuilder("Phase dependencies contain cycle: ").append(name).append(" > ");
               Phase p3 = p;
               do {
                  sb.append(p3.name).append(" > ");
                  p3 = deps.get(p3);
                  assert p3 != null;
               } while (p3 != phase);
               throw new BenchmarkDefinitionException(sb.append(name).toString());
            }
            if (deps.putIfAbsent(p2, p) == null) {
               toProcess.add(p2);
            }
         });
      }
   }

   private void checkDependencies(Phase phase, Collection<String> references, Map<String, Phase> phases) {
      for (String dep : references) {
         if (!phases.containsKey(dep)) {
            String suggestion = phases.keySet().stream()
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

   public BenchmarkBuilder setDefaultAgentProperties(Map<String, String> properties) {
      this.defaultAgentProperties = properties;
      return this;
   }
}
