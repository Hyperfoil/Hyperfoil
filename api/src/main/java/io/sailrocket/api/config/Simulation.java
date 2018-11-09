package io.sailrocket.api.config;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;

public class Simulation implements Serializable {
   private final int threads;
   private final Map<String, Http> http;
   private final Collection<Phase> phases;
   private final Map<String, Object> tags;
   private final long statisticsCollectionPeriod;

   public Simulation(int threads, Map<String, Http> http, Collection<Phase> phases, Map<String, Object> tags, long statisticsCollectionPeriod) {
      this.threads = threads;
      this.http = http;
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
