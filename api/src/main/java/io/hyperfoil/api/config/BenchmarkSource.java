package io.hyperfoil.api.config;

import java.io.Serializable;
import java.util.Map;

public class BenchmarkSource implements Serializable {
   public final String name;
   public final String version;
   public final String yaml;
   public final transient BenchmarkData data;
   public final Map<String, String> paramsWithDefaults;

   public BenchmarkSource(String name, String yaml, BenchmarkData data, Map<String, String> paramsWithDefaults) {
      this.name = name;
      // note: version of template and resulting benchmark don't have to match
      this.version = Benchmark.randomUUID();
      this.yaml = yaml;
      this.data = data;
      this.paramsWithDefaults = paramsWithDefaults;
   }

   public boolean isTemplate() {
      return !paramsWithDefaults.isEmpty();
   }
}
