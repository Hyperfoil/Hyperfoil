package http2.bench.client;

import com.beust.jcommander.Parameters;
import http2.bench.CommandBase;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.Http2EventAdapter;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.SupportedCipherSuiteFilter;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.vertx.core.buffer.Buffer;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@Parameters()
public class ClientCommand extends CommandBase {

//  private static final ByteBuf PAYLOAD = Unpooled.copiedBuffer(new byte[512]);

  @Override
  public void run() throws Exception {

    SslProvider provider = OpenSsl.isAlpnSupported() ? SslProvider.OPENSSL : SslProvider.JDK;
    SslContext sslCtx =  SslContextBuilder.forClient()
        .sslProvider(provider)
                /* NOTE: the cipher filter may not include all ciphers required by the HTTP/2 specification.
                 * Please refer to the HTTP/2 specification for cipher requirements. */
        .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
        .trustManager(InsecureTrustManagerFactory.INSTANCE)
        .applicationProtocolConfig(new ApplicationProtocolConfig(
            ApplicationProtocolConfig.Protocol.ALPN,
            // NO_ADVERTISE is currently the only mode supported by both OpenSsl and JDK providers.
            ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
            // ACCEPT is currently the only mode supported by both OpenSsl and JDK providers.
            ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
            ApplicationProtocolNames.HTTP_2,
            ApplicationProtocolNames.HTTP_1_1))
        .build();

    EventLoopGroup workerGroup = new NioEventLoopGroup();

    int size = 100000;
    int maxConn = 1000;
    AtomicInteger count = new AtomicInteger(size);
    AtomicInteger ok = new AtomicInteger();
    AtomicInteger reset =new AtomicInteger();

    for (int i = 0; i < maxConn;i++) {
      TestClient client = new TestClient(workerGroup, sslCtx);
      client.connect(8443, "localhost", conn -> {
        Runnable r = () -> {
          int step1 = (10 * count.get()) / size;
          int val = count.getAndDecrement();
          if (val > 0) {
            int step2 = (10 * count.get()) / size;
            if (step2 < step1) {
              System.out.println(step2);
            }
            int id = conn.nextStreamId();
            conn.encoder.writeHeaders(conn.context, id, TestClient.POST("/foo").add("content-length", "512"), 0, false, conn.context.newPromise());
            conn.encoder.writeData(conn.context, id, Buffer.buffer(new byte[512]).getByteBuf(), 0, true, conn.context.newPromise());
            conn.context.flush();
          } else if (val == 0){
            System.out.println("DONE " + ok.get() + " / " + reset.get());
          }
        };
        conn.decoder.frameListener(new Http2EventAdapter() {
          @Override
          public void onHeadersRead(ChannelHandlerContext ctx, int streamId, Http2Headers headers, int streamDependency, short weight, boolean exclusive, int padding, boolean endStream) throws Http2Exception {
            ok.incrementAndGet();
            if (endStream) {
              r.run();
            }
          }
          @Override
          public int onDataRead(ChannelHandlerContext ctx, int streamId, ByteBuf data, int padding, boolean endOfStream) throws Http2Exception {
            int v = super.onDataRead(ctx, streamId, data, padding, endOfStream);
            if (endOfStream) {
              r.run();
            }
            return v;
          }
          @Override
          public void onRstStreamRead(ChannelHandlerContext ctx, int streamId, long errorCode) throws Http2Exception {
            reset.incrementAndGet();
            r.run();
          }
        });
        r.run();
      });
    }

  }
}
