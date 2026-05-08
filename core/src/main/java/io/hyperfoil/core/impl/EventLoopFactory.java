package io.hyperfoil.core.impl;

import io.hyperfoil.internal.Properties;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueDatagramChannel;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.kqueue.KQueueSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

public abstract class EventLoopFactory {
   public static final EventLoopFactory INSTANCE;

   static {
      String transport = Properties.get(Properties.NETTY_TRANSPORT, null);
      if (transport != null) {
         switch (transport.toLowerCase()) {
            case "nio":
               INSTANCE = new NioEventLoopFactory();
               break;
            case "epoll":
               INSTANCE = new EpollEventLoopFactory();
               break;
            case "kqueue":
               INSTANCE = new KqueueEventLoopFactory();
               break;
            // TODO: io_uring requires kernel >= 5.9
            default:
               throw new IllegalStateException("Unknown transport '" + transport + "', use either 'nio' or 'epoll'.");
         }
      } else {
         if (Epoll.isAvailable()) {
            INSTANCE = new EpollEventLoopFactory();
         } else if (KQueue.isAvailable()) {
            INSTANCE = new KqueueEventLoopFactory();
         } else {
            INSTANCE = new NioEventLoopFactory();
         }
      }
   }

   public abstract EventLoopGroup create(int threads);

   public abstract Class<? extends SocketChannel> socketChannel();

   public abstract Class<? extends DatagramChannel> datagramChannel();

   private static class NioEventLoopFactory extends EventLoopFactory {
      @Override
      public EventLoopGroup create(int threads) {
         return new NioEventLoopGroup(threads);
      }

      @Override
      public Class<? extends SocketChannel> socketChannel() {
         return NioSocketChannel.class;
      }

      @Override
      public Class<? extends DatagramChannel> datagramChannel() {
         return NioDatagramChannel.class;
      }
   }

   private static class EpollEventLoopFactory extends EventLoopFactory {
      @Override
      public EventLoopGroup create(int threads) {
         return new EpollEventLoopGroup(threads);
      }

      @Override
      public Class<? extends SocketChannel> socketChannel() {
         return EpollSocketChannel.class;
      }

      @Override
      public Class<? extends DatagramChannel> datagramChannel() {
         return EpollDatagramChannel.class;
      }

   }

   private static class KqueueEventLoopFactory extends EventLoopFactory {
      @Override
      public EventLoopGroup create(int threads) {
         return new KQueueEventLoopGroup(threads);
      }

      @Override
      public Class<? extends SocketChannel> socketChannel() {
         return KQueueSocketChannel.class;
      }

      @Override
      public Class<? extends DatagramChannel> datagramChannel() {
         return KQueueDatagramChannel.class;
      }
   }
}
