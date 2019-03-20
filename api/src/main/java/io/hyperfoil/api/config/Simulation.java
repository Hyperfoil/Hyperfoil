package io.hyperfoil.api.config;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;

import io.hyperfoil.util.Immutable;

public class Simulation implements Serializable, Immutable {
   private final int threads;
   private final Ergonomics ergonomics;
   private final Map<String, Http> http;
   private final Http defaultHttp;
   private final Collection<Phase> phases;
   private final Map<String, Object> tags;
   private final long statisticsCollectionPeriod;

   public Simulation(int threads, Ergonomics ergonomics, Map<String, Http> http, Collection<Phase> phases, Map<String, Object> tags, long statisticsCollectionPeriod) {
      this.threads = threads;
      this.ergonomics = ergonomics;
      this.http = http;
      this.defaultHttp = http.values().stream().filter(Http::isDefault).findFirst().orElse(null);
      this.phases = phases;
      this.tags = tags;
      this.statisticsCollectionPeriod = statisticsCollectionPeriod;
   }

   public Collection<Phase> phases() {
      return phases;
   }

   public Map<String, Object> tags() {
      return tags;
   }

   public Map<String, Http> http() {
      return http;
   }

   public Http defaultHttp() {
      return defaultHttp;
   }

   public long statisticsCollectionPeriod() {
      return statisticsCollectionPeriod;
   }

   @Override
   public String toString() {
      return "Simulation{" +
                     ", threads=" + threads +
                     ", http=" + http +
                     ", phases=" + phases +
                     ", tags=" + tags +
                     ", statisticsCollectionPeriod=" + statisticsCollectionPeriod +
                     '}';
   }

   public int threads() {
      return threads;
   }
}
