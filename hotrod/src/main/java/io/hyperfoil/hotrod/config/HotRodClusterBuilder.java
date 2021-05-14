package io.hyperfoil.hotrod.config;

import java.util.ArrayList;
import java.util.List;

public class HotRodClusterBuilder {
   private String uri;
   private List<String> caches = new ArrayList<>();

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
