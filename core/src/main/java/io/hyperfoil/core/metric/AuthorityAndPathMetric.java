package io.hyperfoil.core.metric;

public class AuthorityAndPathMetric implements MetricSelector {

   @Override
   public String apply(String authority, String path) {
      return authority == null ? path : authority + path;
   }
}
