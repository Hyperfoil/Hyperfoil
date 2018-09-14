package io.sailrocket.core.impl;

import java.util.Collection;
import java.util.Map;

import io.sailrocket.api.Phase;
import io.sailrocket.api.Simulation;
import io.sailrocket.spi.HttpClientPoolFactory;

public class SimulationImpl implements Simulation {
   private final HttpClientPoolFactory httpClientPoolFactory;
   private final Collection<Phase> phases;
   private final Map<String, Object> tags;
   private final long statisticsCollectionPeriod;

   public SimulationImpl(HttpClientPoolFactory httpClientPoolFactory, Collection<Phase> phases, Map<String, Object> tags, long statisticsCollectionPeriod) {
      this.httpClientPoolFactory = httpClientPoolFactory;
      this.phases = phases;
      this.tags = tags;
      this.statisticsCollectionPeriod = statisticsCollectionPeriod;
   }

   @Override
   public Collection<Phase> phases() {
      return phases;
   }

   @Override
   public Map<String, Object> tags() {
      return tags;
   }

   @Override
   public HttpClientPoolFactory httpClientPoolFactory() {
      return httpClientPoolFactory;
   }

   @Override
   public long statisticsCollectionPeriod() {
      return statisticsCollectionPeriod;
   }
}
