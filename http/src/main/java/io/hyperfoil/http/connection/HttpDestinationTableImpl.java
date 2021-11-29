package io.hyperfoil.http.connection;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.hyperfoil.api.session.Session;
import io.hyperfoil.http.api.HttpConnectionPool;
import io.hyperfoil.http.api.HttpDestinationTable;

public class HttpDestinationTableImpl implements HttpDestinationTable {
   private final Map<String, HttpConnectionPool> byAuthority;
   private final Map<String, HttpConnectionPool> byName;
   private final String[] authorities;
   private final byte[][] authorityBytes;

   public HttpDestinationTableImpl(Map<String, HttpConnectionPool> byAuthority) {
      this.byAuthority = byAuthority;
      this.byName = byAuthority.values().stream().filter(http -> http.clientPool().config().name() != null)
            .collect(Collectors.toMap(http -> http.clientPool().config().name(), Function.identity()));
      this.authorities = byAuthority.keySet().stream().filter(Objects::nonNull).toArray(String[]::new);
      this.authorityBytes = Stream.of(authorities).map(url -> url.getBytes(StandardCharsets.UTF_8)).toArray(byte[][]::new);
   }

   public HttpDestinationTableImpl(HttpDestinationTable other, Function<HttpConnectionPool, HttpConnectionPool> replacePool) {
      this.authorities = other.authorities();
      this.authorityBytes = other.authorityBytes();
      this.byAuthority = new HashMap<>();
      this.byName = new HashMap<>();
      HttpConnectionPool defaultPool = other.getConnectionPoolByAuthority(null);
      for (String authority : authorities) {
         HttpConnectionPool pool = other.getConnectionPoolByAuthority(authority);
         HttpConnectionPool newPool = replacePool.apply(pool);
         byAuthority.put(authority, newPool);
         if (pool == defaultPool) {
            byAuthority.put(null, newPool);
         }
         byName.put(pool.clientPool().config().name(), newPool);
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
   public void onSessionReset(Session session) {
      byAuthority.values().forEach(HttpConnectionPool::onSessionReset);
   }

   @Override
   public boolean hasSingleDestination() {
      return authorities.length == 1;
   }

   @Override
   public HttpConnectionPool getConnectionPoolByName(String endpoint) {
      return byName.get(endpoint);
   }

   @Override
   public HttpConnectionPool getConnectionPoolByAuthority(String authority) {
      return byAuthority.get(authority);
   }

   public Iterable<Map.Entry<String, HttpConnectionPool>> iterable() {
      return byAuthority.entrySet();
   }
}
