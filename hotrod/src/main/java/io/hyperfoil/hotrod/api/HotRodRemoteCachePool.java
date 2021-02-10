package io.hyperfoil.hotrod.api;

import io.hyperfoil.api.session.Session;
import io.hyperfoil.hotrod.connection.HotRodRemoteCachePoolImpl;

public interface HotRodRemoteCachePool extends Session.Resource {

   Session.ResourceKey<HotRodRemoteCachePool> KEY = new Session.ResourceKey<>() {};

   static HotRodRemoteCachePool get(Session session) {
      return session.getResource(KEY);
   }

   void start();

   void shutdown();

   HotRodRemoteCachePoolImpl.RemoteCacheWithoutToString getRemoteCache(String cacheName);
}
