package io.hyperfoil.api.config;

import java.util.Map;

public abstract class PluginBuilder<E> {
   protected final BenchmarkBuilder parent;

   public PluginBuilder(BenchmarkBuilder parent) {
      this.parent = parent;
   }

   public abstract E ergonomics();

   public abstract void prepareBuild();

   public void addTags(Map<String, Object> tags) {
   }

   public abstract PluginConfig build();

   public BenchmarkBuilder endPlugin() {
      return parent;
   }
}
