package io.sailrocket.api.connection;

import java.io.IOException;

import io.netty.channel.ChannelHandlerContext;

public interface Connection {
   IOException CLOSED_EXCEPTION = new IOException("Connection was closed.");

   ChannelHandlerContext context();

   boolean isAvailable();

   void close();
}
