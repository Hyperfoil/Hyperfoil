package http2.bench.netty;

import com.beust.jcommander.Parameter;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.SupportedCipherSuiteFilter;
import io.netty.handler.ssl.util.SelfSignedCertificate;

import java.util.Collections;

/**
 * A HTTP/2 Server that responds to requests with a Hello World. Once started, you can test the
 * server with the example client.
 */
public class Server {

  static void run(SslProvider sslProvider, int port, int instances) throws Exception {
    // Configure SSL.
    final SslContext sslCtx;
    if (sslProvider != null) {
      SelfSignedCertificate ssc = new SelfSignedCertificate();
      sslCtx = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey())
          .sslProvider(sslProvider)
              /* NOTE: the cipher filter may not include all ciphers required by the HTTP/2 specification.
               * Please refer to the HTTP/2 specification for cipher requirements. */
          .ciphers(Collections.singletonList("TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256"), SupportedCipherSuiteFilter.INSTANCE)
          .applicationProtocolConfig(new ApplicationProtocolConfig(
              ApplicationProtocolConfig.Protocol.ALPN,
              // NO_ADVERTISE is currently the only mode supported by both OpenSsl and JDK providers.
              ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
              // ACCEPT is currently the only mode supported by both OpenSsl and JDK providers.
              ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
              ApplicationProtocolNames.HTTP_2,
              ApplicationProtocolNames.HTTP_1_1))
          .build();
    } else {
      sslCtx = null;
    }
    // Configure the server.
    EventLoopGroup group = new NioEventLoopGroup(instances);
    try {
      ServerBootstrap b = new ServerBootstrap();
      b.option(ChannelOption.SO_BACKLOG, 1024);
      b.group(group)
          .channel(NioServerSocketChannel.class)
//                .handler(new LoggingHandler(LogLevel.INFO))
          .childHandler(new Http2ServerInitializer(sslCtx));

      Channel ch = b.bind(port).sync().channel();

      System.err.println("Open your HTTP/2-enabled web browser and navigate to " +
          (sslProvider != null ? "https" : "http") + "://127.0.0.1:" + port + '/');

      ch.closeFuture().sync();
    } finally {
      group.shutdownGracefully();
    }
  }
}
