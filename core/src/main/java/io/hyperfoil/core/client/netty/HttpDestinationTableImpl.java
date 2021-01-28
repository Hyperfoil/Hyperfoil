package io.hyperfoil.core.client.netty;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

import io.hyperfoil.api.connection.HttpConnectionPool;
import io.hyperfoil.api.connection.HttpDestinationTable;

public class HttpDestinationTableImpl implements HttpDestinationTable {
   private final Map<String, HttpConnectionPool> pools;
   private final String[] authorities;
   private final byte[][] authorityBytes;

   public HttpDestinationTableImpl(Map<String, HttpConnectionPool> pools) {
      this.pools = pools;
      this.authorities = pools.keySet().stream().filter(Objects::nonNull).toArray(String[]::new);
      this.authorityBytes = Stream.of(authorities).map(url -> url.getBytes(StandardCharsets.UTF_8)).toArray(byte[][]::new);
   }

   public HttpDestinationTableImpl(HttpDestinationTable other, Function<HttpConnectionPool, HttpConnectionPool> replacePool) {
      this.authorities = other.authorities();
      this.authorityBytes = other.authorityBytes();
      this.pools = new HashMap<>();
      HttpConnectionPool defaultPool = other.getConnectionPool(null);
      for (String authority : authorities) {
         HttpConnectionPool pool = other.getConnectionPool(authority);
         HttpConnectionPool newPool = replacePool.apply(pool);
         pools.put(authority, newPool);
         if (pool == defaultPool) {
            pools.put(null, newPool);
         }
      }
   }

   @Override
   public String[] authorities() {
      return authorities;
   }

   @Override
   public byte[][] authorityBytes() {
      return authorityBytes;
   }

   @Override
   public void onSessionReset() {
      pools.values().forEach(HttpConnectionPool::onSessionReset);
   }

   @Override
   public boolean hasSingleDestination() {
      return authorities.length == 1;
   }

   @Override
   public HttpConnectionPool getConnectionPool(String authority) {
      return pools.get(authority);
   }

   public Iterable<Map.Entry<String, HttpConnectionPool>> iterable() {
      return pools.entrySet();
   }
}
