package org.infinispan.client.hotrod.impl.transport.netty;

import java.util.concurrent.ExecutorService;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

/**
 * This class works as a replacement of the class in Infinispan Hot Rod Client during tests.
 */
public class TransportHelper {
   static Class<? extends SocketChannel> socketChannel() {
      return NioSocketChannel.class;
   }

   static EventLoopGroup createEventLoopGroup(int maxExecutors, ExecutorService executorService) {
      return shaded.org.infinispan.client.hotrod.impl.transport.netty.TransportHelper.createEventLoopGroup(maxExecutors, executorService);
   }
}
