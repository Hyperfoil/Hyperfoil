package io.sailrocket.core.impl;

import java.util.Collection;

import io.sailrocket.api.Phase;
import io.sailrocket.api.Simulation;
import io.sailrocket.core.client.HttpClientPoolFactory;
import io.vertx.core.json.JsonObject;

public class SimulationImpl implements Simulation {
   private final HttpClientPoolFactory httpClientPoolFactory;
   private final Collection<Phase> phases;
   private final JsonObject tags;

   public SimulationImpl(HttpClientPoolFactory httpClientPoolFactory, Collection<Phase> phases, JsonObject tags) {
      this.httpClientPoolFactory = httpClientPoolFactory;
      this.phases = phases;
      this.tags = tags;
   }

   @Override
   public Collection<Phase> phases() {
      return phases;
   }

   @Override
   public JsonObject tags() {
      return tags;
   }

   public HttpClientPoolFactory httpClientPoolFactory() {
      return httpClientPoolFactory;
   }
}
