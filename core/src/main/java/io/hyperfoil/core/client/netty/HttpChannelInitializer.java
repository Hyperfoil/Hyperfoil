package io.hyperfoil.core.client.netty;

import java.util.function.BiConsumer;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpClientUpgradeHandler;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http2.DefaultHttp2Connection;
import io.netty.handler.codec.http2.Http2ClientUpgradeCodec;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import io.hyperfoil.api.connection.HttpConnection;
import io.hyperfoil.api.connection.HttpConnectionPool;

class HttpChannelInitializer extends ChannelInitializer<Channel> {
   private final HttpClientPoolImpl clientPool;
   private final HttpConnectionPool connectionPool;
   private final BiConsumer<HttpConnection, Throwable> handler;
   private final Http2ConnectionHandlerBuilder http2ConnectionHandlerBuilder;
   private final ApplicationProtocolNegotiationHandler alpnHandler = new ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) {
      @Override
      protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
         ChannelPipeline p = ctx.pipeline();
         if (ApplicationProtocolNames.HTTP_2.equals(protocol)) {
            io.netty.handler.codec.http2.Http2Connection connection = new DefaultHttp2Connection(false);
            CustomHttp2ConnectionHandler clientHandler = http2ConnectionHandlerBuilder.build(connection);
            p.addLast(clientHandler);
         } else if (ApplicationProtocolNames.HTTP_1_1.equals(protocol)) {
            initHttp1xConnection(p);
         } else {
            ctx.close();
            throw new IllegalStateException("unknown protocol: " + protocol);
         }
      }
   };

   HttpChannelInitializer(HttpClientPoolImpl clientPool, HttpConnectionPool connectionPool, BiConsumer<HttpConnection, Throwable> handler) {
      this.clientPool = clientPool;
      this.connectionPool = connectionPool;
      this.handler = handler;
      this.http2ConnectionHandlerBuilder = new Http2ConnectionHandlerBuilder(connectionPool, clientPool.sslContext == null, handler);
   }

   @Override
   protected void initChannel(Channel ch) {
      ChannelPipeline pipeline = ch.pipeline();
      if (clientPool.sslContext != null) {
         pipeline.addLast(clientPool.sslContext.newHandler(ch.alloc(), clientPool.host, clientPool.port));
         pipeline.addLast(alpnHandler);
      } else if (clientPool.forceH2c) {
         io.netty.handler.codec.http2.Http2Connection connection = new DefaultHttp2Connection(false);
         CustomHttp2ConnectionHandler clientHandler = http2ConnectionHandlerBuilder.build(connection);
         HttpClientCodec sourceCodec = new HttpClientCodec();
         Http2ClientUpgradeCodec upgradeCodec = new Http2ClientUpgradeCodec(clientHandler);
         HttpClientUpgradeHandler upgradeHandler = new HttpClientUpgradeHandler(sourceCodec, upgradeCodec, 65536);
         ChannelHandler upgradeRequestHandler = new ChannelInboundHandlerAdapter() {
            @Override
            public void channelActive(ChannelHandlerContext ctx) throws Exception {
               DefaultFullHttpRequest upgradeRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
               upgradeRequest.headers().add(HttpHeaderNames.HOST, clientPool.authority);
               ctx.writeAndFlush(upgradeRequest);
               ctx.fireChannelActive();
               ctx.pipeline().remove(this);
            }
         };
         pipeline.addLast(sourceCodec,
               upgradeHandler,
               upgradeRequestHandler);
      } else {
         initHttp1xConnection(pipeline);
      }
   }

   private void initHttp1xConnection(ChannelPipeline pipeline) {
      Http1xConnection connection = new Http1xConnection(clientPool, connectionPool, handler);
      pipeline.addLast(new Http1xRawBytesHandler(connection));
      pipeline.addLast("codec", new HttpClientCodec(4096, 8192, 8192, false, false));
      pipeline.addLast("handler", connection);
   }
}
