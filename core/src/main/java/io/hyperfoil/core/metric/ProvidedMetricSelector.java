package io.hyperfoil.core.metric;

public class ProvidedMetricSelector implements MetricSelector {
   private String name;

   public ProvidedMetricSelector(String name) {
      this.name = name;
   }

   @Override
   public String apply(String authority, String path) {
      return name;
   }
}
