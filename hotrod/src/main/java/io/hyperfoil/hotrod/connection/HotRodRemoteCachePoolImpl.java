package io.hyperfoil.hotrod.connection;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.client.hotrod.impl.transport.netty.ChannelFactory;

import io.hyperfoil.hotrod.api.HotRodRemoteCachePool;
import io.hyperfoil.hotrod.config.HotRodCluster;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;

public class HotRodRemoteCachePoolImpl implements HotRodRemoteCachePool {

   private final HotRodCluster[] clusters;
   private final EventLoop eventLoop;

   private final Map<String, RemoteCacheManager> remoteCacheManagers = new HashMap<>();
   private final Map<String, RemoteCache<?, ?>> remoteCaches = new HashMap<>();

   public HotRodRemoteCachePoolImpl(HotRodCluster[] clusters, EventLoop eventLoop) {
      this.clusters = clusters;
      this.eventLoop = eventLoop;
   }

   @Override
   public void start() {
      for (HotRodCluster cluster : clusters) {
         Properties properties = new Properties();
         properties.setProperty("infinispan.client.hotrod.default_executor_factory.pool_size", "1");
         ConfigurationBuilder cb = new ConfigurationBuilder().uri(cluster.uri());
         cb.withProperties(properties);
         cb.asyncExecutorFactory().factory(p -> eventLoop);
         RemoteCacheManager remoteCacheManager = new RemoteCacheManager(cb.build());
         this.remoteCacheManagers.put(cluster.uri(), remoteCacheManager);
         validateEventLoop(remoteCacheManager);
         for (String cache : cluster.caches()) {
            remoteCaches.put(cache, remoteCacheManager.getCache(cache));
         }
      }
   }

   private void validateEventLoop(RemoteCacheManager remoteCacheManager) {
      ChannelFactory channelFactory = remoteCacheManager.getChannelFactory();
      try {
         Field eventLoopField = ChannelFactory.class.getDeclaredField("eventLoopGroup");
         eventLoopField.setAccessible(true);
         EventLoopGroup actualEventLoop = (EventLoopGroup) eventLoopField.get(channelFactory);
         if (actualEventLoop != eventLoop) {
            throw new IllegalStateException("Event loop was not injected correctly. This is a classpath issue.");
         }
      } catch (NoSuchFieldException | IllegalAccessException e) {
         throw new IllegalStateException(e);
      }
      ExecutorService asyncExecutorService = remoteCacheManager.getAsyncExecutorService();
      if (asyncExecutorService != eventLoop) {
         throw new IllegalStateException("Event loop was not configured correctly.");
      }
   }

   @Override
   public void shutdown() {
      this.remoteCacheManagers.values().forEach(RemoteCacheManager::stop);
   }

   @Override
   public RemoteCacheWithoutToString<?, ?> getRemoteCache(String cacheName) {
      return new RemoteCacheWithoutToString(this.remoteCaches.get(cacheName));
   }

   /*
    * While debugging, the toString method of RemoteCache will do a block call
    *  at org.infinispan.client.hotrod.impl.RemoteCacheSupport.size(RemoteCacheSupport.java:397)
    *  at org.infinispan.client.hotrod.impl.RemoteCacheImpl.isEmpty(RemoteCacheImpl.java:275)
    * This prevent us of configuring each IDE in order to debug a code
    */
   public static class RemoteCacheWithoutToString<K, V> {
      private RemoteCache<K, V> remoteCache;
      public RemoteCacheWithoutToString(RemoteCache remoteCache) {
         this.remoteCache = remoteCache;
      }

      public CompletableFuture<V> putAsync(K key, V value) {
         return remoteCache.putAsync(key, value);
      }

      public CompletableFuture<V> getAsync(K key) {
         return remoteCache.getAsync(key);
      }
   }
}
