package http2.bench.client;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import http2.bench.CommandBase;
import io.netty.buffer.ByteBuf;
import io.netty.channel.EventLoop;
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
import io.vertx.core.buffer.Buffer;
import org.HdrHistogram.ConcurrentHistogram;
import org.HdrHistogram.Histogram;

import java.io.File;
import java.io.PrintStream;
import java.net.URI;
import java.nio.file.Files;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Todo : use pool buffers wherever possible
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@Parameters()
public class ClientCommand extends CommandBase {

  @Parameter(names = {"-d","--duration"})
  public long duration = 60 * 1000; // in ms for now

  @Parameter(names = {"-c","--connections"})
  public int clients = 1;

  @Parameter(names = "--latency")
  public String latencyPath = null;

  @Parameter(names = {"-b", "--body"})
  public String body = null;

  @Parameter(names = {"-m", "--max-concurrent-streams"})
  public int maxConcurrentStream = 1;

  @Parameter
  public List<String> uri;

  private ByteBuf payload;
  private AtomicInteger connectFailures = new AtomicInteger();
  private AtomicInteger requestCount = new AtomicInteger();
  private AtomicInteger responseCount = new AtomicInteger();
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
  private volatile long endTime;
  private final AtomicBoolean done = new AtomicBoolean();
  private final AtomicInteger connCount = new AtomicInteger();
  private final EventLoopGroup workerGroup = new NioEventLoopGroup();
  private SslContext sslCtx;

  @Override
  public void run() throws Exception {

    if (body != null) {
      try {
        int size = Integer.parseInt(body);
        if (size > 0) {
          byte[] bytes = new byte[size];
          Random r = new Random();
          for (int i = 0;i < bytes.length;i++) {
            bytes[i] = (byte)('A' + r.nextInt(27));
          }
          payload = Buffer.buffer(bytes).getByteBuf();
        }
      } catch (NumberFormatException ignore) {
      }
      if (payload == null) {
        File f = new File(body);
        if (!f.exists() || !f.isFile()) {
          throw new Exception("could not open file " + body);
        }
        payload = Buffer.buffer(Files.readAllBytes(f.toPath())).getByteBuf();
      }
    }

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
    endTime = startTime + duration;
    System.out.println("starting benchmark...");
    System.out.format("%d total client(s)%n", clients);
    checkConnections();
    printDetail(workerGroup.next(), 0, 10);
  }

  private void printDetail(EventLoop el, int count, int total) {
    if (count < total) {
      el.schedule(() -> {
        System.out.format("progress: %d%% done. total requests %d %d/%d %n", ((count + 1) * 100) / total, requestCount.get(), Connection.count(), clients);
        printDetail(el, count + 1, total);
      }, (endTime - startTime) / 10, TimeUnit.MILLISECONDS);
    }
  }

  private void checkConnections() {
    if (System.currentTimeMillis() < endTime) {
      if (connCount.getAndUpdate(v -> v <= clients ? v + 1 : v) < clients) {
        Client client = new Client(workerGroup, sslCtx);
        client.connect(port, host, (conn, err) -> {
          if (err == null) {
            checkRequests(conn);
            checkConnections();
          } else {
            err.printStackTrace();
            connCount.decrementAndGet();
            connectFailures.getAndIncrement();
            if (!reportDone()) {
              checkConnections();
            }
          }
        });
      }
    }
  }

  private void checkRequests(Connection conn) {
    while (conn.numActiveStreams() < maxConcurrentStream) {
      if (!done.get()) {
        doRequest(conn);
      } else {
        break;
      }
    }
  }

  private void doRequest(Connection conn) {
    requestCount.incrementAndGet();
    long startTime = System.nanoTime();
    conn.request(payload != null ? "POST" : "GET", path, stream -> {
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
        responseCount.incrementAndGet();
        if (!reportDone()) {
          long endTime = System.nanoTime();
          histogram.recordValue((endTime - startTime) / 1000);
          checkRequests(conn);
        }
      }).end(payload);
    });
  }

  private boolean reportDone() {
    if (System.currentTimeMillis() < endTime) {
      return false;
    } else {
      if (done.compareAndSet(false, true)) {
        while (Connection.totalStreamCount.get() > 0) {
          System.out.println(Connection.totalStreamCount.get());
          try {
            Thread.sleep(10);
          } catch (InterruptedException ignore) {
          }
        }
        end();
      }
      return true;
    }
  }

  private void end() {
    double elapsedSeconds = elapsedTime();
    Histogram cp = histogram.copy();
    System.out.format("finished in %.2fs, %.2fs req/s%n", elapsedSeconds, requestCount.get() / elapsedSeconds);
    System.out.format("requests: %d total, %d errored%n", requestCount.get(), connectFailures.get());
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
    if (latencyPath != null) {
      try (PrintStream ps = new PrintStream(latencyPath)) {
        cp.outputPercentileDistribution(ps, 1000.0);
      } catch(Exception e) {
        e.printStackTrace();
      }
    }
    Connection.shutdownAll();
    workerGroup.shutdownGracefully(0, 10, TimeUnit.SECONDS);
  }

  private double elapsedTime() {
    long elapsed = System.currentTimeMillis() - startTime;
    return elapsed / 1000D;
  }
}
