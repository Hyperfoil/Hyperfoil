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
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.hyperfoil.impl.FutureSupplier;

/**
 * @author <a href="mailto:stalep@gmail.com">Ståle Pedersen</a>
 */
public class BenchmarkBuilder {

   private final BenchmarkSource source;
   private final Map<String, String> params;
   private BenchmarkData data;
   private String name;
   private Map<String, String> defaultAgentProperties = Collections.emptyMap();
   private final Collection<Agent> agents = new ArrayList<>();
   private final Map<Class<? extends PluginBuilder<?>>, PluginBuilder<?>> plugins = new HashMap<>();
   private int threads = 1;
   private final Map<String, PhaseBuilder<?>> phaseBuilders = new HashMap<>();
   private long statisticsCollectionPeriod = 1000;
   private String triggerUrl;
   private final List<RunHook> preHooks = new ArrayList<>();
   private final List<RunHook> postHooks = new ArrayList<>();
   private Benchmark.FailurePolicy failurePolicy = Benchmark.FailurePolicy.CANCEL;

   public static Collection<PhaseBuilder<?>> phasesForTesting(BenchmarkBuilder builder) {
      return builder.phaseBuilders.values();
   }

   public BenchmarkBuilder(BenchmarkSource source, Map<String, String> params) {
      this.source = source;
      this.params = params;
      this.data = source == null ? BenchmarkData.EMPTY : source.data;
   }

   public static BenchmarkBuilder builder() {
      return new BenchmarkBuilder(null, Collections.emptyMap());
   }

   public BenchmarkSource source() {
      return source;
   }

   public BenchmarkBuilder name(String name) {
      this.name = name;
      return this;
   }

   public String name() {
      return name;
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

   public BenchmarkBuilder failurePolicy(Benchmark.FailurePolicy policy) {
      failurePolicy = policy;
      return this;
   }

   public void prepareBuild() {
      plugins.values().forEach(PluginBuilder::prepareBuild);
      phaseBuilders.values().forEach(PhaseBuilder::prepareBuild);
   }

   public Benchmark build() {
      prepareBuild();
      FutureSupplier<Benchmark> bs = new FutureSupplier<>();

      AtomicInteger phaseIdCounter = new AtomicInteger(0);
      Map<String, Phase> phases = phaseBuilders.values().stream()
            .flatMap(builder -> builder.build(bs, phaseIdCounter).stream()).collect(Collectors.toMap(Phase::name, p -> p));
      for (Phase phase : phases.values()) {
         // check if referenced dependencies exist
         checkDependencies(phase, phase.startAfter, phases);
         checkDependencies(phase, phase.startAfterStrict, phases);
         checkDependencies(phase, phase.terminateAfterStrict, phases);
         if (phase.startWithDelay != null) {
            checkDependencies(phase, Collections.singletonList(phase.startWithDelay.phase), phases);
         }
         checkStartWith(phase);
         checkDeadlock(phase, phases);
      }
      Map<String, Object> tags = new HashMap<>();
      plugins.values().forEach(builder -> builder.addTags(tags));
      tags.put("threads", threads);

      // It is important to gather files only after all other potentially file-reading builders
      // are done.
      Map<String, byte[]> files = data.files();

      Agent[] agents = this.agents.stream().map(a -> {
         HashMap<String, String> properties = new HashMap<>(defaultAgentProperties);
         properties.putAll(a.properties);
         return new Agent(a.name, a.inlineConfig, properties);
      }).toArray(Agent[]::new);
      Map<Class<? extends PluginConfig>, PluginConfig> plugins = this.plugins.values().stream()
            .map(PluginBuilder::build).collect(Collectors.toMap(PluginConfig::getClass, Function.identity()));
      Benchmark benchmark = new Benchmark(name, Benchmark.randomUUID(), source, params, files, agents, threads, plugins,
            new ArrayList<>(phases.values()), tags, statisticsCollectionPeriod, triggerUrl, preHooks, postHooks, failurePolicy);
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
         // consider all referenced dependencies (startAfter, startAfterStrict and startWithDelay)
         // ensure there are no cyclic references.
         p.getDependencies().forEach(name -> {
            Phase p2 = phases.get(name);
            if (p2 == null) {
               // non-existent phase, will be reported later
               return;
            } else if (p2 == phase) {
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

   /**
    * Check that a phase does not have both startWith and any other start* set
    *
    * @param phase phase to check
    */
   private void checkStartWith(Phase phase) {
      if (phase.startWithDelay != null && (!phase.startAfter.isEmpty() || !phase.startAfterStrict.isEmpty() || phase.startTime > 0)) {
         throw new BenchmarkDefinitionException("Phase " + phase.name + " has both startWith and one of startAfter, startAfterStrict and startTime set.");
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

   public BenchmarkData data() {
      return data;
   }

   public BenchmarkBuilder data(BenchmarkData data) {
      this.data = data;
      return this;
   }

   public BenchmarkBuilder setDefaultAgentProperties(Map<String, String> properties) {
      this.defaultAgentProperties = properties;
      return this;
   }

   @SuppressWarnings("unchecked")
   public <T extends PluginBuilder<?>> T plugin(Class<T> clz) {
      return (T) plugins.get(clz);
   }

   public <P extends PluginBuilder<?>> P addPlugin(Function<BenchmarkBuilder, P> ctor) {
      P plugin = ctor.apply(this);
      @SuppressWarnings("unchecked")
      Class<? extends PluginBuilder<?>> pluginClass = (Class<? extends PluginBuilder<?>>) plugin.getClass();
      PluginBuilder<?> prev = plugins.putIfAbsent(pluginClass, plugin);
      if (prev != null) {
         throw new BenchmarkDefinitionException("Adding the same plugin twice! " + plugin.getClass().getName());
      }
      return plugin;
   }
}
