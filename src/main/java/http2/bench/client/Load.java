package http2.bench.client;

import io.netty.buffer.ByteBuf;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.ssl.SslContext;
import io.vertx.core.http.HttpVersion;
import org.HdrHistogram.ConcurrentHistogram;
import org.HdrHistogram.Histogram;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class Load {

  private final int threads;
  private final int rate;
  private final int pacerRate;
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
  private final Report report;

  private final Histogram histogram = new ConcurrentHistogram(TimeUnit.MINUTES.toNanos(1), 2);
  private LongAdder connectFailureCount = new LongAdder();
  private LongAdder requestCount = new LongAdder();
  private LongAdder responseCount = new LongAdder();
  private LongAdder status_2xx = new LongAdder();
  private LongAdder status_3xx = new LongAdder();
  private LongAdder status_4xx = new LongAdder();
  private LongAdder status_5xx = new LongAdder();
  private LongAdder status_other = new LongAdder();
  private LongAdder[] statuses = {status_2xx, status_3xx, status_4xx, status_5xx, status_other};
  private LongAdder resetCount = new LongAdder();
  private volatile long startTime;
  private HttpClient client;
  private volatile boolean done;

  public Load(int threads, int rate, long duration, long warmup, HttpVersion protocol, EventLoopGroup workerGroup,
              SslContext sslCtx, int port, String host, String path, ByteBuf payload, int maxQueue, int connections,
              Report report) {
    this.threads = threads;
    this.rate = rate;
    this.pacerRate = rate / threads;
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
    this.report = report;
  }

  Report run() throws Exception {
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
    new Worker().runSlot(warmup);
    System.out.println("starting rate=" + rate);
    startTime = System.nanoTime();
    requestCount.reset();
    responseCount.reset();
    client.resetStatistics();
    printDetail(workerGroup.next());
    int requestCount;
    ExecutorService exec = Executors.newFixedThreadPool(threads);
    Worker[] workers = new Worker[threads];
    for (int i = 0; i < threads; i++) {
      workers[i] = new Worker(exec);
    }
    List<CompletableFuture<Integer>> results = new ArrayList<>(threads);
    for (Worker worker : workers) {
      results.add(worker.runSlot(duration));
    }
    requestCount = 0;
    for (CompletableFuture<Integer> result : results) {
      requestCount += result.get();
    }
    exec.shutdown();
    return end(requestCount);
  }

  private double ratio() {
    long end = Math.min(System.nanoTime(), startTime + duration);
    long expected = rate * (end - startTime) / 1000000000;
    return requestCount.doubleValue() / (double) expected;
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
        double progress = (100 * (System.nanoTime() - startTime)) / (double) duration;
        System.out.format("progress: %.2f%% done - total requests/responses %d/%d, ratio %.2f, read %d kb/s, written %d kb/s%n",
            progress,
            requestCount.intValue(),
            responseCount.intValue(),
            ratio(), readThroughput(), writeThroughput());
        printDetail(scheduler);
      }
    }, 5, TimeUnit.SECONDS);
  }

  class Worker {

    private final Executor exec;

    public Worker(Executor exec) {
      this.exec = exec;
    }

    public Worker() {
      this(Runnable::run);
    }

    private CompletableFuture<Integer> runSlot(long duration) {
      CompletableFuture<Integer> result = new CompletableFuture<>();
      runSlot(duration, result::complete);
      return result;
    }

    private void runSlot(long duration, Consumer<Integer> doneHandler) {
      exec.execute(() -> {
        if (duration > 0) {
          long slotBegins = System.nanoTime();
          long slotEnds = slotBegins + duration;
          Pacer pacer = new Pacer(pacerRate);
          pacer.setInitialStartTime(slotBegins);
          int result = doRequestInSlot(pacer, slotEnds);
          if (doneHandler != null) {
            doneHandler.accept(result);
          }
        }
      });
    }

    private int doRequestInSlot(Pacer pacer, long slotEnds) {
      int numReq = 0;
      while (true) {
        long now = System.nanoTime();
        if (now > slotEnds) {
          return numReq;
        } else {
          HttpConnection conn = client.choose();
          if (conn != null) {
            numReq++;
            pacer.acquire(1);
            doRequest(conn);
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
      }
    }

    private void doRequest(HttpConnection conn) {
      requestCount.increment();
      long startTime = System.nanoTime();
      conn.request(payload != null ? "POST" : "GET", path, stream -> {
        if (payload != null) {
          stream.putHeader("content-length", "" + payload.readableBytes());
        }
        stream.headersHandler(frame -> {
          int status = frame.status();
          if (status >= 0 && status < statuses.length) {
            statuses[status].increment();
          }
        }).resetHandler(frame -> {
          resetCount.increment();
        }).endHandler(v -> {
          responseCount.increment();
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
  }

  private Report end(int requestCount) {
    done = true;
    long expectedRequests = rate * TimeUnit.NANOSECONDS.toSeconds(duration);
    long elapsed = System.nanoTime() - startTime;
    Histogram cp = histogram.copy();
    cp.setStartTimeStamp(TimeUnit.NANOSECONDS.toMillis(startTime));
    cp.setEndTimeStamp(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()));
    report.measures(
        expectedRequests,
        elapsed,
        cp,
        responseCount.intValue(),
        ratio(),
        connectFailureCount.intValue(),
        resetCount.intValue(),
        requestCount,
        Stream.of(statuses).mapToInt(LongAdder::intValue).toArray(),
        client.bytesRead(),
        client.bytesWritten()
    );
    client.shutdown();
    return report;
  }
}
