package io.hyperfoil.hotrod.config;

import java.io.Serializable;

import org.infinispan.client.hotrod.impl.HotRodURI;

public class HotRodCluster implements Serializable {

   // https://infinispan.org/blog/2020/05/26/hotrod-uri
   private final String uri;
   private final String[] caches;

   public HotRodCluster(String uri, String[] caches) {
      // used to validate the uri
      HotRodURI.create(uri);

      this.uri = uri;
      this.caches = caches;
   }

   public String uri() {
      return this.uri;
   }

   public String[] caches() {
      return this.caches;
   }
}
