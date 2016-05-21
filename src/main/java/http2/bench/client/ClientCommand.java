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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Todo : use pool buffers wherever possible
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@Parameters()
public class ClientCommand extends CommandBase {

  @Parameter(names = {"-d","--duration"})
  public String durationParam = "30s";

  @Parameter(names = {"-c","--connections"})
  public int clientsParam = 1;

  @Parameter(names = "--latency")
  public String latencyParam = null;

  @Parameter(names = {"-b", "--body"})
  public String bodyParam = null;

  @Parameter(names = {"-m", "--max-concurrent-streams"})
  public int maxConcurrentStream = 1;

  @Parameter
  public List<String> uriParam;

  @Parameter(names = {"-r", "--rate"})
  public int rateParam = 100; // rate per second

  @Parameter(names = {"-w", "--warmup"})
  public String warmupParam = "0";

  private ByteBuf payload;
  private String payloadLength;
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
  private long duration;
  private long warmup;
  private String host;
  private int port;
  private String path;
  private Histogram histogram = new ConcurrentHistogram(3600000000000L, 2);
  private volatile long startTime;
  private volatile long endTime;
  private final EventLoopGroup workerGroup = new NioEventLoopGroup();
  private SslContext sslCtx;
  private Client client;
  private EventLoop scheduler;

  private static long parseDuration(String s) {
    TimeUnit unit;
    String prefix;
    switch (s.charAt(s.length() - 1)) {
      case 's':
        unit = TimeUnit.SECONDS;
        prefix = s.substring(0, s.length() - 1);
        break;
      case 'm':
        unit = TimeUnit.MINUTES;
        prefix = s.substring(0, s.length() - 1);
        break;
      case 'h':
        unit = TimeUnit.HOURS;
        prefix = s.substring(0, s.length() - 1);
        break;
      default:
        unit = TimeUnit.SECONDS;
        prefix = s;
        break;
    }
    return unit.toMillis(Long.parseLong(prefix));
  }

  @Override
  public void run() throws Exception {

    duration = parseDuration(durationParam);
    warmup = parseDuration(warmupParam);

    if (bodyParam != null) {
      try {
        int size = Integer.parseInt(bodyParam);
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
        File f = new File(bodyParam);
        if (!f.exists() || !f.isFile()) {
          throw new Exception("could not open file " + bodyParam);
        }
        payload = Buffer.buffer(Files.readAllBytes(f.toPath())).getByteBuf();
      }
      payloadLength = "" + payload.readableBytes();
    }

    if (uriParam == null || uriParam.size() < 1) {
      throw new Exception("no URI or input file given");
    }

    URI absoluteURI = new URI(uriParam.get(0));
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

    client = new Client(workerGroup, sslCtx, clientsParam, port, host);
    System.out.println("starting benchmark...");
    System.out.format("%d total client(s)%n", clientsParam);
    start();
    scheduler = workerGroup.next();
  }

  private double ratio() {
    long end = Math.min(System.currentTimeMillis(), endTime);
    long expected = rateParam * (end - startTime) / 1000;
    return requestCount.get() / (double)expected;
  }

  private void printDetail(int count, int total) {
    if (count < total) {
      scheduler.schedule(() -> {
        System.out.format("progress: %d%% done. total requests/responses %d/%d, ratio %.2f%n", ((count + 1) * 100) / total, requestCount.get(), responseCount.get(), ratio());
        printDetail(count + 1, total);
      }, (endTime - startTime) / 10, TimeUnit.MILLISECONDS);
    }
  }

  private void start() {
    client.start(v1 -> {
      System.out.println("connection(s) created...");
      if (warmup > 0) {
        System.out.println("warming up...");
      }
      runSlots(warmup, v -> {
        System.out.println("working on it...");
        startTime = System.currentTimeMillis();
        endTime = startTime + duration;
        requestCount.set(0);
        responseCount.set(0);
        printDetail(0, 10);
        int numSlots = (int)(duration / 1000);
        int lastSlot = (int)(duration % 1000);
        startSlot(numSlots, lastSlot, v2 -> {
          end();
        });
      });
    });
  }

  private void runSlots(long duration, Consumer<Void> doneHandler) {
    if (duration > 0) {
      int numSlots = (int)(duration / 1000);
      int lastSlot = (int)(duration % 1000);
      startSlot(numSlots, lastSlot, doneHandler);
    } else {
      doneHandler.accept(null);
    }
  }

  private void startSlot(int numSlots, int lastSlot, Consumer<Void> doneHandler) {
    long slotBegins = System.currentTimeMillis();
    if (numSlots > 0) {
      doRequestInSlot(rateParam, slotBegins + 1000, v -> {
        startSlot(numSlots -1, lastSlot, doneHandler);
      });
    } else {
      doRequestInSlot(rateParam, slotBegins + lastSlot, doneHandler);
    }
  }

  private void doRequestInSlot(long remaining, long slotEnds, Consumer<Void> doneHandler) {
    long now = System.currentTimeMillis();
    if (remaining > 0) {
      if (now > slotEnds) {
        doneHandler.accept(null);
      } else {
        Connection conn = client.choose(maxConcurrentStream);
        if (conn != null) {
          doRequest(conn);
        } else {
          // Miss a request here
        }
        long delay = (long)(TimeUnit.MILLISECONDS.toNanos(slotEnds - now) / (double) remaining);
        scheduler.schedule(() -> doRequestInSlot(remaining - 1, slotEnds, doneHandler), delay, TimeUnit.NANOSECONDS);
      }
    } else {
      long delay = TimeUnit.MILLISECONDS.toNanos(slotEnds - now);
      if (delay > 0) {
        scheduler.schedule(() -> {
          doneHandler.accept(null);
        }, delay, TimeUnit.NANOSECONDS);
      } else {
        doneHandler.accept(null);
      }
    }
  }

  private void doRequest(Connection conn) {
    requestCount.incrementAndGet();
    long startTime = System.nanoTime();
    conn.request(payload != null ? "POST" : "GET", path, stream -> {
      if (payload != null) {
        stream.putHeader("content-length", payloadLength);
      }
      stream.headersHandler(frame -> {
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
        long endTime = System.nanoTime();
        histogram.recordValueWithExpectedInterval((endTime - startTime) / 1000, 255);
      });
      if (payload != null) {
        stream.end(payload.duplicate());
      } else {
        stream.end();
      }
    });
  }

  private void end() {
    long expectedRequests = (long)(rateParam * duration / 1000D);
    double elapsedSeconds = elapsedTime();
    Histogram cp = histogram.copy();
    System.out.format("finished in %.2fs, %.2fs req/s, %.2fs ratio%n", elapsedSeconds, responseCount.get() / elapsedSeconds,ratio());
    System.out.format("requests: %d total, %d errored, %d expected%n", responseCount.get(), connectFailures.get(), expectedRequests);
    System.out.format("status codes: %d 2xx, %d 3xx, %d 4xx, %d 5xx, %d others%n", statuses[0].get(), statuses[1].get(), statuses[2].get(), statuses[3].get(), statuses[4].get());
//    System.out.println("DONE ok=" + status_200.get() + " / reset=" + reset.get() + " / connectFailures=" + connectFailures.getAndIncrement());
    try {
      System.out.println("mean   = " + cp.getMean());
      System.out.println("max    = " + cp.getMaxValueAsDouble());
      System.out.println("90%    = " + cp.getValueAtPercentile(0.90));
      System.out.println("99%    = " + cp.getValueAtPercentile(0.99));
      System.out.println("99.9%  = " + cp.getValueAtPercentile(0.999));
      System.out.println("99.99% = " + cp.getValueAtPercentile(0.9999));
    } catch (Exception e) {
      e.printStackTrace();
    }
    if (latencyParam != null) {
      try (PrintStream ps = new PrintStream(latencyParam)) {
        cp.outputPercentileDistribution(ps, 1000.0);
      } catch(Exception e) {
        e.printStackTrace();
      }
    }
    client.shutdown();
    workerGroup.shutdownGracefully(0, 10, TimeUnit.SECONDS);
  }

  private double elapsedTime() {
    long elapsed = System.currentTimeMillis() - startTime;
    return elapsed / 1000D;
  }
}
