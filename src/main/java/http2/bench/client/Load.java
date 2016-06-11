package http2.bench.client;

import io.netty.buffer.ByteBuf;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.ssl.SslContext;
import io.vertx.core.http.HttpVersion;
import org.HdrHistogram.ConcurrentHistogram;
import org.HdrHistogram.Histogram;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Stream;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class Load {

  private final int rate;
  private final long duration;
  private final long warmup;
  private final HttpVersion protocol;
  private final EventLoopGroup workerGroup;
  private final SslContext sslCtx;
  private final int port;
  private final String host;
  private final String path;
  private final ByteBuf payload;
  private final int maxQueue;
  private final int connections;

  private AtomicInteger connectFailureCount = new AtomicInteger();
  private AtomicInteger requestCount = new AtomicInteger();
  private AtomicInteger responseCount = new AtomicInteger();
  private AtomicInteger status_2xx = new AtomicInteger();
  private AtomicInteger status_3xx = new AtomicInteger();
  private AtomicInteger status_4xx = new AtomicInteger();
  private AtomicInteger status_5xx = new AtomicInteger();
  private AtomicInteger status_other = new AtomicInteger();
  private AtomicInteger[] statuses = {status_2xx, status_3xx, status_4xx, status_5xx, status_other};
  private AtomicInteger resetCount = new AtomicInteger();
  private LongAdder missedRequests = new LongAdder();
  private Histogram histogram = new ConcurrentHistogram(TimeUnit.MINUTES.toNanos(1), 2);
  private volatile long startTime;
  private HttpClient client;
  private volatile boolean done;

  public Load(int rate, long duration, long warmup, HttpVersion protocol, EventLoopGroup workerGroup,
              SslContext sslCtx, int port, String host, String path, ByteBuf payload, int maxQueue, int connections) {
    this.rate = rate;
    this.duration = duration;
    this.warmup = warmup;
    this.protocol = protocol;
    this.workerGroup = workerGroup;
    this.sslCtx = sslCtx;
    this.port = port;
    this.host = host;
    this.path = path;
    this.payload = payload;
    this.maxQueue = maxQueue;
    this.connections = connections;
  }

  Report run() {
    CountDownLatch latch = new CountDownLatch(1);
    if (protocol == HttpVersion.HTTP_2) {
      client = new Http2Client(workerGroup, sslCtx, connections, port, host, maxQueue);
    } else {
      client = new Http1xClient(workerGroup, sslCtx, connections, port, host, maxQueue);
    }
    client.start(v1 -> {
      latch.countDown();
    });
    try {
      latch.await(100, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      e.printStackTrace();
      return null;
    }
    System.out.println("connection(s) created...");
    if (warmup > 0) {
      System.out.println("warming up...");
    }
    runSlots(warmup);
    System.out.println("starting rate=" + rate);
    startTime = System.nanoTime();
    requestCount.set(0);
    responseCount.set(0);
    client.resetStatistics();
    missedRequests.reset();
    printDetail(workerGroup.next());
    int numSlots = (int) (duration / 1000000000);
    long lastSlot = (int) (duration % 1000000000);
    startSlot(numSlots, lastSlot);
    return end();
  }

  private double ratio() {
    long end = Math.min(System.nanoTime(), startTime + duration);
    long expected = rate * (end - startTime) / 1000000000;
    return requestCount.get() / (double) expected;
  }

  private long readThroughput() {
    return client.bytesRead() / (TimeUnit.NANOSECONDS.toSeconds((System.nanoTime() - startTime)) * 1024);
  }

  private long writeThroughput() {
    return client.bytesWritten() / (TimeUnit.NANOSECONDS.toSeconds((System.nanoTime() - startTime) * 1024));
  }

  private void printDetail(EventLoop scheduler) {
    scheduler.schedule(() -> {
      if (!done) {
        double progress = (100 * (System.nanoTime() - startTime)) / (double)duration;
        System.out.format("progress: %.2f%% done - total requests/responses %d/%d, ratio %.2f, read %d kb/s, written %d kb/s%n",
            progress,
            requestCount.get(),
            responseCount.get(),
            ratio(), readThroughput(), writeThroughput());
        printDetail(scheduler);
      }
    }, 5, TimeUnit.SECONDS);
  }

  private void runSlots(long duration) {
    if (duration > 0) {
      int numSlots = (int) (duration / 1000000000);
      long lastSlot = duration % 1000000000;
      startSlot(numSlots, lastSlot);
    }
  }

  private void startSlot(int numSlots, long lastSlot) {
    while (numSlots-- > 0) {
      abc(rate, 1000000000);
    }
    abc((int) ((rate * lastSlot) / 1000000000), lastSlot);
  }

  private void abc(int remainingInSlot, long duration) {
    long slotBegins = System.nanoTime();
    long slotEnds = slotBegins + duration;
    Pacer pacer = new Pacer(rate);
    pacer.setInitialStartTime(slotBegins);
    doRequestInSlot(pacer, remainingInSlot, slotEnds);
  }

  private void doRequestInSlot(Pacer pacer, int remainingInSlot, long slotEnds) {
    while (true) {
      long now = System.nanoTime();
      if (remainingInSlot > 0) {
        if (now > slotEnds) {
          missedRequests.add(remainingInSlot);
          return;
        } else {
          HttpConnection conn = client.choose();
          if (conn != null) {
            remainingInSlot--;
            long expectedStartTimeNanos = pacer.expectedNextOperationNanoTime();
            pacer.acquire(1);
            doRequest(conn, expectedStartTimeNanos);
          } else {
            // Sleep a little
/*
          long stop = System.nanoTime() + 10;
          while (System.nanoTime() < stop) {
            Thread.yield();
          }
*/
          }
        }
      } else {
        long delay = slotEnds - now;
        if (delay > 0) {
          Pacer.sleepNs(delay);
          return;
        } else {
          return;
        }
      }
    }
  }

  private void doRequest(HttpConnection conn, long expectedStartTimeNanos) {
    requestCount.incrementAndGet();
    long startTime = System.nanoTime();
    conn.request(payload != null ? "POST" : "GET", path, stream -> {
      if (payload != null) {
        stream.putHeader("content-length", "" + payload.readableBytes());
      }
      stream.headersHandler(frame -> {
        int status = frame.status();
        if (status >= 0 && status < statuses.length) {
          statuses[status].incrementAndGet();
        }
      }).resetHandler(frame -> {
        resetCount.incrementAndGet();
      }).endHandler(v -> {
        responseCount.incrementAndGet();
        long endTime = System.nanoTime();
        long durationMillis = endTime - startTime;
        histogram.recordValue(durationMillis);
      });
      if (payload != null) {
        stream.end(payload.duplicate());
      } else {
        stream.end();
      }
    });
  }

  private Report end() {
    done = true;
    long expectedRequests = rate * TimeUnit.NANOSECONDS.toSeconds(duration);
    long elapsed = System.nanoTime() - startTime;
    Histogram cp = histogram.copy();
    cp.setStartTimeStamp(TimeUnit.NANOSECONDS.toMillis(startTime));
    cp.setEndTimeStamp(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()));
    Report report = new Report(
        expectedRequests,
        elapsed,
        cp,
        responseCount.get(),
        ratio(),
        connectFailureCount.get(),
        resetCount.get(),
        missedRequests.intValue(),
        Stream.of(statuses).mapToInt(AtomicInteger::intValue).toArray(),
        client.bytesRead(),
        client.bytesWritten());
    client.shutdown();
    return report;
  }
}
