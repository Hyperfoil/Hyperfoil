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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * A benchmark is a collection of simulation, user,
 * SLA and scaling strategy (Ramp up, Steady State, Ramp Down, steady state variance)
 * that are to be run against the target environment.
 */
public class Benchmark implements Serializable {

   private final String name;
   private final String version;
   private final Map<String, String> params;
   @Visitor.Ignore
   private final BenchmarkSource source;
   @Visitor.Ignore
   private final Map<String, byte[]> files;
   private final Agent[] agents;
   private final int defaultThreads;
   private final int totalThreads;
   @Visitor.Invoke(method = "plugins")
   private final Map<Class<? extends PluginConfig>, PluginConfig> plugins;
   private final Collection<Phase> phases;
   private final Map<String, Object> tags;
   private final long statisticsCollectionPeriod;
   private final String triggerUrl;
   private final List<RunHook> preHooks;
   private final List<RunHook> postHooks;
   private final FailurePolicy failurePolicy;

   public static Benchmark forTesting() {
      return BenchmarkBuilder.builder().build();
   }

   public Benchmark(String name, String version, BenchmarkSource source, Map<String, String> params, Map<String, byte[]> files,
                    Agent[] agents, int defaultThreads,
                    Map<Class<? extends PluginConfig>, PluginConfig> plugins, Collection<Phase> phases,
                    Map<String, Object> tags, long statisticsCollectionPeriod, String triggerUrl,
                    List<RunHook> preHooks, List<RunHook> postHooks, FailurePolicy failurePolicy) {
      this.name = name;
      this.params = params;
      this.source = source;
      this.files = files;
      this.agents = agents;
      this.defaultThreads = defaultThreads;
      this.plugins = plugins;
      this.failurePolicy = failurePolicy;
      this.totalThreads = agents.length == 0 ? defaultThreads : Stream.of(agents).mapToInt(Agent::threads).map(n -> n <= 0 ? defaultThreads : n).sum();
      this.phases = phases;
      this.tags = tags;
      this.statisticsCollectionPeriod = statisticsCollectionPeriod;
      this.triggerUrl = triggerUrl;
      this.preHooks = preHooks;
      this.postHooks = postHooks;
      this.version = version;
   }

   static String randomUUID() {
      ThreadLocalRandom random = ThreadLocalRandom.current();
      return new UUID(random.nextLong(), random.nextLong()).toString();
   }

   public static Benchmark empty(String name, Map<String, String> templateParams) {
      return new Benchmark(name, randomUUID(), null, templateParams,
            Collections.emptyMap(), new Agent[0], 0, Collections.emptyMap(), Collections.emptyList(),
            Collections.emptyMap(), 0, null, Collections.emptyList(), Collections.emptyList(), FailurePolicy.CANCEL);
   }

   public String name() {
      return name;
   }

   public String version() {
      return version;
   }

   public Agent[] agents() {
      return agents;
   }

   /**
    * As the transformation from YAML is one-way (due to forks and iterations)
    * here we store the original source (be it YAML or JSON)
    *
    * @return Source YAML for the benchmark.
    */
   public BenchmarkSource source() {
      return source;
   }

   public Map<String, String> params() {
      return params;
   }

   public Map<String, byte[]> files() {
      return files;
   }

   public int defaultThreads() {
      return defaultThreads;
   }

   public Collection<Phase> phases() {
      return phases;
   }

   public Map<String, Object> tags() {
      return tags;
   }

   public long statisticsCollectionPeriod() {
      return statisticsCollectionPeriod;
   }

   public String triggerUrl() {
      return triggerUrl;
   }

   public List<RunHook> preHooks() {
      return preHooks;
   }

   public List<RunHook> postHooks() {
      return postHooks;
   }

   @Override
   public String toString() {
      return "Benchmark{name='" + name + '\'' +
            ", source='" + source + '\'' +
            ", agents=" + Arrays.toString(agents) +
            ", threads=" + defaultThreads +
            ", plugins=" + plugins +
            ", phases=" + phases +
            ", tags=" + tags +
            ", statisticsCollectionPeriod=" + statisticsCollectionPeriod +
            '}';
   }

   public Stream<Step> steps() {
      return phases().stream()
            .flatMap(phase -> Stream.of(phase.scenario().sequences()))
            .flatMap(sequence -> Stream.of(sequence.steps()));
   }

   public Phase[] phasesById() {
      Phase[] phases = new Phase[this.phases.size()];
      this.phases.forEach(p -> phases[p.id()] = p);
      return phases;
   }

   public int slice(int totalValue, int agentId) {
      if (agents.length == 0) {
         return totalValue;
      }
      int threads = threads(agentId);
      int prevThreads = IntStream.range(0, agentId).map(this::threads).sum();
      return (prevThreads + threads) * totalValue / totalThreads - prevThreads * totalValue / totalThreads;
   }

   public double slice(double totalValue, int agentId) {
      if (agents.length == 0) {
         return totalValue;
      }
      return totalValue * threads(agentId) / totalThreads;
   }

   public int threads(int agentId) {
      if (agents.length == 0) {
         return defaultThreads;
      }
      int threads = agents()[agentId].threads();
      return threads <= 0 ? defaultThreads() : threads;
   }

   public int totalThreads() {
      return totalThreads;
   }

   @SuppressWarnings("unchecked")
   public <T extends PluginConfig> T plugin(Class<T> clazz) {
      return (T) plugins.get(clazz);
   }

   public Collection<PluginConfig> plugins() {
      return plugins.values();
   }

   public FailurePolicy failurePolicy() {
      return failurePolicy;
   }

   public enum FailurePolicy {
      CANCEL,
      CONTINUE,
   }
}
