package io.hyperfoil.api.connection;

import java.io.IOException;

import io.netty.channel.ChannelHandlerContext;

public interface Connection {
   IOException CLOSED_EXCEPTION = new IOException("Connection was unexpectedly closed.");
   IOException SELF_CLOSED_EXCEPTION = new IOException("Connection was closed by us.");

   ChannelHandlerContext context();

   void onAcquire();

   /**
    * Cancels one pending acquisition slot (decrements aboutToSend).
    * Called by the pool when a consumer that was handed this connection is gone
    * and will never call request(), so the slot must be returned.
    */
   void cancelAcquire();

   boolean isAvailable();

   int inFlight();

   /**
    * Returns the number of requests that are actually on the wire (sent but not yet responded),
    * excluding pending acquisition slots (aboutToSend).
    */
   int pendingRequestCount();

   /**
    * This is an external request to close the connection
    */
   void close();

   /**
    * Invoked by the pool when the connection got closed.
    */
   void setClosed();

   boolean isOpen();

   boolean isClosed();

   String host();
}
