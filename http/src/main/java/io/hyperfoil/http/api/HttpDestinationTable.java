package io.hyperfoil.http.api;

import io.hyperfoil.api.session.Session;

/**
 * Manages all {@link HttpConnectionPool http connection pools} for sessions in single executor.
 */
public interface HttpDestinationTable extends Session.Resource {
   Session.ResourceKey<HttpDestinationTable> KEY = new Session.ResourceKey<>() {
   };

   HttpConnectionPool getConnectionPoolByName(String endpoint);

   HttpConnectionPool getConnectionPoolByAuthority(String authority);

   String[] authorities();

   byte[][] authorityBytes();

   boolean hasSingleDestination();

   static HttpDestinationTable get(Session session) {
      return session.getResource(KEY);
   }
}
