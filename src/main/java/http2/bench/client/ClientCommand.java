package http2.bench.client;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import http2.bench.CommandBase;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.SupportedCipherSuiteFilter;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@Parameters()
public class ClientCommand extends CommandBase {

  private static final ByteBuf PAYLOAD = Unpooled.copiedBuffer(new byte[512]);

  @Parameter(names = "--requests")
  public int requests = 1;

  @Parameter(names = "--clients")
  public int clients = 1;

  @Parameter
  public List<String> uri;

  private AtomicInteger connectFailures = new AtomicInteger();
  private AtomicInteger count = new AtomicInteger();
  private AtomicInteger ok = new AtomicInteger();
  private AtomicInteger reset = new AtomicInteger();
  private String path;

  @Override
  public void run() throws Exception {

    if (uri == null || uri.size() < 1) {
      throw new Exception("no URI or input file given");
    }

    URI absoluteURI = new URI(uri.get(0));
    String host = absoluteURI.getHost();
    int port = absoluteURI.getPort();
    path = absoluteURI.getPath();
    count.set(requests);

    SslProvider provider = OpenSsl.isAlpnSupported() ? SslProvider.OPENSSL : SslProvider.JDK;
    SslContext sslCtx = SslContextBuilder.forClient()
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

    for (int i = 0; i < clients; i++) {
      Client client = new Client(workerGroup, sslCtx);
      client.connect(port, host, this::onConnect);
    }
  }

  private void onConnect(Connection conn, Throwable err) {
    if (err == null) {
      doRequest(conn);
    } else {
      connectFailures.getAndIncrement();
    }
  }

  private void doRequest(Connection conn) {
    conn.request("POST", path, stream -> {
      stream
          .putHeader("content-length", "512")
          .headersHandler(headers -> {
            ok.incrementAndGet();
          }).resetHandler(frame -> {
        reset.incrementAndGet();
      }).endHandler(v -> {
        int step1 = (10 * count.get()) / requests;
        int val = count.getAndDecrement();
        if (val > 0) {
          int step2 = (10 * count.get()) / requests;
          if (step2 < step1) {
            System.out.println(step2);
          }
          doRequest(conn);
        } else if (val == 0) {
          System.out.println("DONE ok=" + ok.get() + " / reset=" + reset.get() + " / connectFailures=" + connectFailures.getAndIncrement());
        }
      }).end(PAYLOAD);
    });
  }
}
