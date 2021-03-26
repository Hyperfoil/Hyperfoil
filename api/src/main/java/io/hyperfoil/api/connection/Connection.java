package io.hyperfoil.api.connection;

import java.io.IOException;

import io.netty.channel.ChannelHandlerContext;

public interface Connection {
   IOException CLOSED_EXCEPTION = new IOException("Connection was unexpectedly closed.");
   IOException SELF_CLOSED_EXCEPTION = new IOException("Connection was closed by us.");

   ChannelHandlerContext context();

   void onAcquire();

   boolean isAvailable();

   int inFlight();

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
