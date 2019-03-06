package io.hyperfoil.core.client.netty;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import io.hyperfoil.api.connection.HttpConnectionPool;
import io.hyperfoil.api.connection.HttpDestinationTable;

public class HttpDestinationTableImpl implements HttpDestinationTable {
   private final Map<String, HttpConnectionPool> pools;
   private final String[] baseUrls;
   private final byte[][] baseUrlBytes;


   public HttpDestinationTableImpl(Map<String, HttpConnectionPool> pools) {
      this.pools = pools;
      this.baseUrls = pools.keySet().stream().filter(Objects::nonNull).toArray(String[]::new);
      this.baseUrlBytes = Stream.of(baseUrls).map(url -> url.getBytes(StandardCharsets.UTF_8)).toArray(byte[][]::new);
   }

   @Override
   public String[] baseUrls() {
      return baseUrls;
   }

   @Override
   public byte[][] baseUrlBytes() {
      return baseUrlBytes;
   }

   @Override
   public HttpConnectionPool getConnectionPool(String baseUrl) {
      return pools.get(baseUrl);
   }

   public Iterable<Map.Entry<String, HttpConnectionPool>> iterable() {
      return pools.entrySet();
   }
}
