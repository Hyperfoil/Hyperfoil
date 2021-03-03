package io.hyperfoil.http.config;

public enum ConnectionStrategy {
   /**
    * Connections are created in a pool and then borrowed by the session.
    * When the request is complete the connection is returned to the shared pool.
    */
   SHARED_POOL,
   /**
    * Connections are created in a shared pool. When the request is completed
    * it is not returned to the shared pool but to a session-local pool.
    * Subsequent requests by this session first try to acquire the connection from
    * this local pool.
    * When the session completes all connections from the session-local pool are returned
    * to the shared pool.
    */
   SESSION_POOLS,
   /**
    * Connections are created before request or borrowed from a session-local pool.
    * When the request is completed the connection is returned to this pool.
    * When the session completes all connections from the session-local pool are closed.
    */
   OPEN_ON_REQUEST,
   /**
    * Always create the connection before the request and close it when it is complete.
    * No pooling of connections.
    */
   ALWAYS_NEW
}
