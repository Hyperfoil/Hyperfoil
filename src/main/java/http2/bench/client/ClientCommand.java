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
import org.HdrHistogram.ConcurrentHistogram;
import org.HdrHistogram.Histogram;

import java.io.PrintStream;
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

  @Parameter(names = "--histogram")
  public String histogramPath = null;

  @Parameter
  public List<String> uri;

  private AtomicInteger connectFailures = new AtomicInteger();
  private AtomicInteger count = new AtomicInteger();
  private AtomicInteger status_2xx = new AtomicInteger();
  private AtomicInteger status_3xx = new AtomicInteger();
  private AtomicInteger status_4xx = new AtomicInteger();
  private AtomicInteger status_5xx = new AtomicInteger();
  private AtomicInteger status_other = new AtomicInteger();
  private AtomicInteger[] statuses = { status_2xx, status_3xx, status_4xx, status_5xx, status_other };
  private AtomicInteger reset = new AtomicInteger();
  private String host;
  private int port;
  private String path;
  private Histogram histogram = new ConcurrentHistogram(3600000000000L, 3);
  private volatile long startTime;
  private final AtomicInteger connCount = new AtomicInteger();
  private final EventLoopGroup workerGroup = new NioEventLoopGroup();
  private SslContext sslCtx;

  @Override
  public void run() throws Exception {

    if (uri == null || uri.size() < 1) {
      throw new Exception("no URI or input file given");
    }

    URI absoluteURI = new URI(uri.get(0));
    host = absoluteURI.getHost();
    port = absoluteURI.getPort();
    path = absoluteURI.getPath();

    SslProvider provider = OpenSsl.isAlpnSupported() ? SslProvider.OPENSSL : SslProvider.JDK;
    sslCtx = SslContextBuilder.forClient()
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


    startTime = System.currentTimeMillis();
    System.out.println("starting benchmark...");
    System.out.format("%d total client(s). %d total requests%n", clients, requests);
    check();
  }

  private void check() {
    while (count.incrementAndGet() < requests) {
      int a = connCount.getAndUpdate(v -> v <= clients ? v + 1 : v);
      if (a < clients) {
        Client client = new Client(workerGroup, sslCtx);
        client.connect(port, host, (conn, err) -> {
          if (err == null) {
            doRequest(conn);
          } else {
            connCount.decrementAndGet();
            connectFailures.getAndIncrement();
            check();
          }
        });
      } else {
        break;
      }
    }
  }

  private void checkRequest(Connection conn) {
    int val = count.getAndIncrement();
    if (val < requests) {
      int step1 = (10 * val) / requests;
      int step2 = (10 * (val + 1)) / requests;
      if (step2 > step1) {
        System.out.format("progress: %d%% done%n", step2 * 10);
      }
      doRequest(conn);
    } else if (val == requests) {
      end();
    }
  }

  private void doRequest(Connection conn) {
    long startTime = System.nanoTime();
    conn.request("POST", path, stream -> {
      stream
          .putHeader("content-length", "512")
          .headersHandler(frame -> {
            try {
              int status = (Integer.parseInt(frame.headers.status().toString()) - 200) / 100;
              if (status >= 0 && status < statuses.length) {
                statuses[status].incrementAndGet();
              }
            } catch (NumberFormatException ignore) {
            }
          }).resetHandler(frame -> {
        reset.incrementAndGet();
      }).endHandler(v -> {
        long endTime = System.nanoTime();
        histogram.recordValue((endTime - startTime) / 1000);
        checkRequest(conn);
      }).end(PAYLOAD);
    });
  }

  private void end() {
    long elapsed = System.currentTimeMillis() - startTime;
    Histogram cp = histogram.copy();
    double elapsedSeconds = elapsed / 1000D;
    System.out.format("finished in %.2fs, %.2fs req/s%n", elapsedSeconds, requests / elapsedSeconds);
    System.out.format("requests: %d total, %d errored%n", requests, connectFailures.get());
    System.out.format("status codes: %d 2xx, %d 3xx, %d 4xx, %d 5xx, %d others%n", statuses[0].get(), statuses[1].get(), statuses[2].get(), statuses[3].get(), statuses[4].get());
//    System.out.println("DONE ok=" + status_200.get() + " / reset=" + reset.get() + " / connectFailures=" + connectFailures.getAndIncrement());
    try {
      System.out.println("mean   = " + cp.getMean());
      System.out.println("max    = " + cp.getMaxValue());
      System.out.println("90%    = " + cp.getValueAtPercentile(0.90));
      System.out.println("99%    = " + cp.getValueAtPercentile(0.99));
      System.out.println("99.9%  = " + cp.getValueAtPercentile(0.999));
      System.out.println("99.99% = " + cp.getValueAtPercentile(0.9999));
    } catch (Exception e) {
      e.printStackTrace();
    }
    if (histogramPath != null) {
      try (PrintStream ps = new PrintStream(histogramPath)) {
        cp.outputPercentileDistribution(ps, 1000.0);
      } catch(Exception e) {
        e.printStackTrace();
      }
    }
    System.exit(0);
  }
}
