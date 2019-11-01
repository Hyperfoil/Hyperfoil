package io.hyperfoil.api.connection;

/**
 * Manages all {@link HttpConnectionPool http connection pools} for sessions in single executor.
 */
public interface HttpDestinationTable {
   HttpConnectionPool getConnectionPool(String authority);

   String[] authorities();

   byte[][] authorityBytes();

   void onSessionReset();
}
