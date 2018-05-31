package http2.bench.client;

import io.netty.buffer.ByteBuf;
import io.netty.channel.EventLoop;
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

  private final HttpClientBuilder clientBuilder;
  private final int threads;
  private final int rate;
  private final int pacerRate;
  private final long duration;
  private final long warmup;
  private final String path;
  private final ByteBuf payload;
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

  public Load(int threads, int rate, long duration, long warmup,
              HttpClientBuilder clientBuilder, String path, ByteBuf payload,
              Report report) {
    this.threads = threads;
    this.rate = rate;
    this.pacerRate = rate / threads;
    this.duration = duration;
    this.warmup = warmup;
    this.clientBuilder = clientBuilder;
    this.path = path;
    this.payload = payload;
    this.report = report;
  }

  Report run() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    client = clientBuilder.build();
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
    // printDetail(workerGroup.next());
    ExecutorService exec = Executors.newFixedThreadPool(threads);
    Worker[] workers = new Worker[threads];
    for (int i = 0; i < threads; i++) {
      workers[i] = new Worker(exec);
    }
    List<CompletableFuture<Void>> results = new ArrayList<>(threads);
    for (Worker worker : workers) {
      results.add(worker.runSlot(duration));
    }
    for (CompletableFuture<Void> result : results) {
      result.get();
    }
    exec.shutdown();
    return end(requestCount.intValue());
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

  static class ScheduledRequest {

    final long startTime;
    ScheduledRequest next;

    public ScheduledRequest(long startTime) {
      this.startTime = startTime;
    }
  }

  class Worker {

    private final Executor exec;
    private ScheduledRequest head;
    private ScheduledRequest tail;

    public Worker(Executor exec) {
      this.exec = exec;
    }

    public Worker() {
      this(Runnable::run);
    }

    private CompletableFuture<Void> runSlot(long duration) {
      CompletableFuture<Void> result = new CompletableFuture<>();
      runSlot(duration, result::complete);
      return result;
    }

    private void runSlot(long duration, Consumer<Void> doneHandler) {
      exec.execute(() -> {
        if (duration > 0) {
          long slotBegins = System.nanoTime();
          long slotEnds = slotBegins + duration;
          Pacer pacer = new Pacer(pacerRate);
          pacer.setInitialStartTime(slotBegins);
          doRequestInSlot(pacer, slotEnds);
          if (doneHandler != null) {
            doneHandler.accept(null);
          }
        }
      });
    }

    private void doRequestInSlot(Pacer pacer, long slotEnds) {
      while (true) {
        long now = System.nanoTime();
        if (now > slotEnds) {
          return;
        } else {
          ScheduledRequest schedule = new ScheduledRequest(now);
          if (head == null) {
            head = tail = schedule;
          } else {
            tail.next = schedule;
            tail = schedule;
          }
          checkPending();
          pacer.acquire(1);
        }
      }
    }

    private void checkPending() {
      HttpRequest conn;
      while (head != null && (conn = client.request(payload != null ? HttpMethod.POST : HttpMethod.GET, path)) != null) {
        long startTime = head.startTime;
        head = head.next;
        if (head == null) {
          tail = null;
        }
        doRequest(conn, startTime);
      }
    }

    private void doRequest(HttpRequest request, long startTime) {
      requestCount.increment();
      if (payload != null) {
        request.putHeader("content-length", "" + payload.readableBytes());
      }
      request.headersHandler(code -> {
        int status = (code - 200) / 100;
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
//          checkPending();
      });
      if (payload != null) {
        request.end(payload.duplicate());
      } else {
        request.end();
      }
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
