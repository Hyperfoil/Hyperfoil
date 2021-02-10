package io.hyperfoil.hotrod.config;

import java.util.ArrayList;
import java.util.List;

import io.hyperfoil.api.config.Rewritable;

public class HotRodClusterBuilder implements Rewritable<HotRodClusterBuilder> {
   private String uri;
   private List<String> caches = new ArrayList<>();

   @Override
   public void readFrom(HotRodClusterBuilder other) {
      this.uri = other.uri;
      this.caches = new ArrayList<>(other.caches);
   }

   public HotRodClusterBuilder uri(String uri) {
      this.uri = uri;
      return this;
   }

   public HotRodClusterBuilder addCache(String cache) {
      this.caches.add(cache);
      return this;
   }

   public HotRodCluster build() {
      return new HotRodCluster(uri, caches.toArray(String[]::new));
   }
}
