package io.hyperfoil.hotrod.config;

import io.hyperfoil.api.config.PluginConfig;

public class HotRodPluginConfig implements PluginConfig {
   private final HotRodCluster[] clusters;

   public HotRodPluginConfig(HotRodCluster[] clusters) {
      this.clusters = clusters;
   }

   public HotRodCluster[] clusters() {
      return clusters;
   }
}
