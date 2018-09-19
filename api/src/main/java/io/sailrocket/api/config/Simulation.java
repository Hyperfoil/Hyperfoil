package io.sailrocket.api.config;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;

import io.sailrocket.api.connection.HttpClientPoolFactory;

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
}
