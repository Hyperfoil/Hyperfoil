package io.hyperfoil.api.connection;

/**
 * Manages all {@link HttpConnectionPool http connection pools} for sessions in single executor.
 */
public interface HttpDestinationTable {
   HttpConnectionPool getConnectionPool(String baseUrl);

   String[] baseUrls();

   byte[][] baseUrlBytes();
}
