package shaded.org.infinispan.client.hotrod.impl.transport.netty;

import java.util.concurrent.ExecutorService;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

/**
 * This class will replace <code>org.infinispan.client.hotrod.impl.transport.netty.TransportHelper</code>
 * in the shaded dependency uberjar. It is used to override event loop creation inside Infinispan
 * in order to share the executor with Hyperfoil.
 */
public class TransportHelper {
   public static Class<? extends SocketChannel> socketChannel() {
      return NioSocketChannel.class;
   }

   public static EventLoopGroup createEventLoopGroup(int maxExecutors, ExecutorService executorService) {
      if (maxExecutors != 1) {
         throw new IllegalArgumentException("The eventloop should be single-threaded!");
      }
      if (executorService instanceof EventLoopGroup) {
         return (EventLoopGroup) executorService;
      } else {
         throw new IllegalStateException("Hyperfoil is supposed to pass the event loop as executor service");
      }
   }
}
