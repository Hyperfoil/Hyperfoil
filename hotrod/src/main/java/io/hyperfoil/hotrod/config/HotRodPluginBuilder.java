package io.hyperfoil.hotrod.config;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.PluginBuilder;
import io.hyperfoil.api.config.PluginConfig;

public class HotRodPluginBuilder extends PluginBuilder<HotRodErgonomics> {
   private final List<HotRodClusterBuilder> clusters = new ArrayList<>();

   public HotRodPluginBuilder(BenchmarkBuilder parent) {
      super(parent);
   }

   @Override
   public HotRodErgonomics ergonomics() {
      return null;
   }

   @Override
   public void prepareBuild() {
   }

   @Override
   public PluginConfig build() {
      HotRodCluster[] clusters = this.clusters.stream().map(HotRodClusterBuilder::build).toArray(HotRodCluster[]::new);
      if (clusters.length == 0) {
         throw new BenchmarkDefinitionException("No clusters set!");
      } else if (Stream.of(clusters).map(HotRodCluster::uri).distinct().count() != clusters.length) {
         throw new BenchmarkDefinitionException("Cluster definition with duplicate uris!");
      }
      return new HotRodPluginConfig(clusters);
   }

   public HotRodClusterBuilder addCluster() {
      HotRodClusterBuilder builder = new HotRodClusterBuilder();
      clusters.add(builder);
      return builder;
   }
}
