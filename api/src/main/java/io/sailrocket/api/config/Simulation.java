package io.sailrocket.api.config;

import io.sailrocket.api.connection.HttpClientPoolFactory;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;

public class Simulation implements Serializable {
   private final HttpClientPoolFactory httpClientPoolFactory;
   private final Collection<Phase> phases;
   private final Map<String, Object> tags;
   private final long statisticsCollectionPeriod;

   public Simulation(HttpClientPoolFactory httpClientPoolFactory, Collection<Phase> phases, Map<String, Object> tags, long statisticsCollectionPeriod) {
      this.httpClientPoolFactory = httpClientPoolFactory;
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

   public HttpClientPoolFactory httpClientPoolFactory() {
      return httpClientPoolFactory;
   }

   public long statisticsCollectionPeriod() {
      return statisticsCollectionPeriod;
   }

   @Override
   public String toString() {
      return "Simulation{" +
                     "httpClientPoolFactory=" + httpClientPoolFactory +
                     ", phases=" + phases +
                     ", tags=" + tags +
                     ", statisticsCollectionPeriod=" + statisticsCollectionPeriod +
                     '}';
   }
}
