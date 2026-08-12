/*
 * Copyright 2026 The Netty VirtualThread Scheduler Project
 *
 * The Netty VirtualThread Scheduler Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package io.hyperfoil.mock;

import java.util.concurrent.TimeUnit;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;

/**
 * A minimal HTTP 1.1 mock server using plain Netty. Fast startup, configurable
 * thread count and think time.
 * <p>
 * Arguments: [port] [thinkTimeMs] [threads]
 * <ul>
 * <li>port - HTTP port (default: 8080)</li>
 * <li>thinkTimeMs - delay before response in ms (default: 100)</li>
 * <li>threads - number of event loop threads (default: available
 * processors)</li>
 * </ul>
 */
public class MockHttpServer {

   // Pre-computed cached response - a JSON list of fruits
   private static final ByteBuf CACHED_RESPONSE = Unpooled.unreleasableBuffer(Unpooled.copiedBuffer("""
         {
           "fruits": [
             {"name": "Apple", "color": "Red", "price": 1.20},
             {"name": "Banana", "color": "Yellow", "price": 0.50},
             {"name": "Orange", "color": "Orange", "price": 0.80},
             {"name": "Grape", "color": "Purple", "price": 2.00},
             {"name": "Mango", "color": "Yellow", "price": 1.50},
             {"name": "Strawberry", "color": "Red", "price": 3.00},
             {"name": "Blueberry", "color": "Blue", "price": 4.00},
             {"name": "Pineapple", "color": "Yellow", "price": 2.50},
             {"name": "Watermelon", "color": "Green", "price": 5.00},
             {"name": "Kiwi", "color": "Brown", "price": 1.00}
           ]
         }
         """, CharsetUtil.UTF_8));

   private static final ByteBuf HEALTH_RESPONSE = Unpooled
         .unreleasableBuffer(Unpooled.copiedBuffer("OK", CharsetUtil.UTF_8));

   private final int port;
   private final long thinkTimeNs;
   private final int threads;
   private final boolean silent;
   private EventLoopGroup workerGroup;
   private Channel serverChannel;

   public MockHttpServer(int port, double thinkTimeMs, int threads) {
      this(port, thinkTimeMs, threads, false);
   }

   public MockHttpServer(int port, double thinkTimeMs, int threads, boolean silent) {
      this.port = port;
      this.thinkTimeNs = (long) (thinkTimeMs * 1_000_000);
      this.threads = threads > 0 ? threads : Runtime.getRuntime().availableProcessors();
      this.silent = silent;
   }

   public void start() throws InterruptedException {
      Class<? extends ServerSocketChannel> serverChannelClass;
      if (Epoll.isAvailable()) {
         workerGroup = new EpollEventLoopGroup(threads);
         serverChannelClass = EpollServerSocketChannel.class;
      } else {
         workerGroup = new NioEventLoopGroup(threads);
         serverChannelClass = NioServerSocketChannel.class;
      }

      ServerBootstrap b = new ServerBootstrap();
      b.group(workerGroup).channel(serverChannelClass).childOption(ChannelOption.TCP_NODELAY, true)
            .childHandler(new ChannelInitializer<SocketChannel>() {
               @Override
               protected void initChannel(SocketChannel ch) {
                  ChannelPipeline p = ch.pipeline();
                  p.addLast(new HttpServerCodec());
                  p.addLast(new HttpObjectAggregator(65536));
                  p.addLast(new HttpHandler(thinkTimeNs));
               }
            });

      serverChannel = b.bind(port).sync().channel();
      if (!silent) {
         System.out.printf("Mock HTTP Server started on port %d with %dns think time using %d thread(s)%n", port,
               thinkTimeNs, threads);
      }
   }

   public void stop() {
      if (serverChannel != null) {
         serverChannel.close();
      }
      if (workerGroup != null) {
         workerGroup.shutdownGracefully();
      }
      if (!silent) {
         System.out.println("Server stopped");
      }
   }

   private static class HttpHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
      private final long thinkTimeNs;

      HttpHandler(long thinkTimeMs) {
         this.thinkTimeNs = thinkTimeMs;
      }

      @Override
      protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
         String uri = request.uri();
         boolean keepAlive = HttpUtil.isKeepAlive(request);

         if (uri.equals("/health")) {
            sendResponse(ctx, HEALTH_RESPONSE.duplicate(), "text/plain", keepAlive);
            return;
         }

         if (uri.equals("/fruits") || uri.equals("/")) {
            if (thinkTimeNs > 0) {
               // Schedule response after think time delay
               ctx.executor().schedule(
                     () -> sendResponse(ctx, CACHED_RESPONSE.duplicate(), "application/json", keepAlive),
                     thinkTimeNs, TimeUnit.NANOSECONDS);
            } else {
               sendResponse(ctx, CACHED_RESPONSE.duplicate(), "application/json", keepAlive);
            }
            return;
         }

         // 404 for unknown paths
         ByteBuf content = Unpooled.copiedBuffer("Not Found", CharsetUtil.UTF_8);
         FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND,
               content);
         response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain");
         response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
         ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
      }

      private void sendResponse(ChannelHandlerContext ctx, ByteBuf content, String contentType, boolean keepAlive) {
         FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
               content);
         response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);
         response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());

         if (keepAlive) {
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            ctx.writeAndFlush(response);
         } else {
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
         }
      }

      @Override
      public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
         cause.printStackTrace();
         ctx.close();
      }
   }

   public static void main(String[] args) throws InterruptedException {
      int port = 8080;
      double thinkTimeMs = 100;
      Integer threads = null;
      boolean silent = false;

      for (int i = 0; i < args.length; i++) {
         switch (args[i]) {
            case "--port" -> port = Integer.parseInt(args[++i]);
            case "--think-time" -> thinkTimeMs = Double.parseDouble(args[++i]);
            case "--threads" -> threads = Integer.parseInt(args[++i]);
            case "--silent" -> silent = true;
            default -> {
               // Legacy positional args support
               if (i == 0)
                  port = Integer.parseInt(args[i]);
               else if (i == 1)
                  thinkTimeMs = Double.parseDouble(args[i]);
               else if (i == 2)
                  threads = Integer.parseInt(args[i]);
            }
         }
      }

      int resolvedThreads = threads != null ? threads : Runtime.getRuntime().availableProcessors();
      MockHttpServer server = new MockHttpServer(port, thinkTimeMs, resolvedThreads, silent);
      server.start();

      // Add shutdown hook for graceful shutdown
      Runtime.getRuntime().addShutdownHook(new Thread(server::stop));

      // Block main thread
      server.serverChannel.closeFuture().sync();
   }
}
