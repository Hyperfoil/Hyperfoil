package io.hyperfoil.core.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.hyperfoil.api.BenchmarkExecutionException;
import io.hyperfoil.internal.Properties;

public class CpuWatchdog implements Runnable {
   private static final Logger log = LogManager.getLogger(CpuWatchdog.class);
   private static final Path PROC_STAT = Path.of("/proc/stat");
   private static final long PERIOD = Properties.getLong(Properties.CPU_WATCHDOG_PERIOD, 5000);
   private static final double IDLE_THRESHOLD = Double
         .parseDouble(Properties.get(Properties.CPU_WATCHDOG_IDLE_THRESHOLD, "0.2"));
   // On most architectures the tick is defined as 1/100 of second (10 ms)
   // where the value of 100 can be obtained using sysconf(_SC_CLK_TCK)
   private static final long TICK_NANOS = Properties.getLong("io.hyperfoil.clock.tick.nanos", 10_000_000);

   private final Consumer<Throwable> errorHandler;
   private final Thread thread;
   private final BooleanSupplier warmupTest;
   private final int nCpu;
   private final long[] idleTime;
   private volatile boolean running = true;
   private long lastTimestamp;
   private long now;
   private final Map<String, PhaseRecord> phaseStart = new HashMap<>();
   private final Map<String, String> phaseUsage = new HashMap<>();

   public CpuWatchdog(Consumer<Throwable> errorHandler, BooleanSupplier warmupTest) {
      this.errorHandler = errorHandler;
      this.warmupTest = warmupTest;
      File stat = PROC_STAT.toFile();
      if (!stat.exists() || !stat.isFile() || !stat.canRead()) {
         log.warn("Not starting CPU watchdog as {} is not available (exists: {}, file: {}, readable: {})",
               PROC_STAT, stat.exists(), stat.isFile(), stat.canRead());
         thread = null;
         nCpu = 0;
         idleTime = null;
         return;
      }
      thread = new Thread(this, "cpu-watchdog");
      thread.setDaemon(true);
      AtomicInteger counter = new AtomicInteger();
      if (readProcStat(ignored -> counter.incrementAndGet())) {
         nCpu = counter.get();
      } else {
         nCpu = 0;
      }
      idleTime = new long[nCpu];
   }

   public void run() {
      if (nCpu <= 0) {
         log.error("Illegal number of CPUs");
         return;
      }
      lastTimestamp = System.nanoTime();
      now = lastTimestamp;
      while (running) {
         if (!readProcStat(this::processCpuLine)) {
            log.info("CPU watchdog is terminating.");
            return;
         }
         try {
            Thread.sleep(PERIOD);
         } catch (InterruptedException e) {
            log.error("CPU watchdog thread interrupted, terminating.", e);
            return;
         }
         lastTimestamp = now;
         now = System.nanoTime();
      }
   }

   private boolean readProcStat(Consumer<String[]> consumer) {
      try {
         for (String line : Files.readAllLines(PROC_STAT)) {
            if (!line.startsWith("cpu"))
               continue;
            String[] parts = line.split(" ");
            // ignore overall stats
            if ("cpu".equals(parts[0]))
               continue;
            // weird format?
            if (parts.length < 5)
               continue;

            consumer.accept(parts);
         }
         return true;
      } catch (IOException e) {
         log.error("CPU watchdog cannot read " + PROC_STAT, e);
         return false;
      } catch (NumberFormatException e) {
         log.error("CPU watchdog cannot parse stats.", e);
         return false;
      }
   }

   private void processCpuLine(String[] parts) {
      int cpuIndex = Integer.parseInt(parts[0], 3, parts[0].length(), 10);
      long idle = Long.parseLong(parts[4]);

      long prevIdle = idleTime[cpuIndex];
      if (prevIdle != 0 && prevIdle != Long.MAX_VALUE && lastTimestamp != now) {
         double idleRatio = (double) (TICK_NANOS * (idle - prevIdle)) / (now - lastTimestamp);
         if (idleRatio < IDLE_THRESHOLD) {
            String message = String.format("%s | CPU %d was used for %.0f%% which is more than the threshold of %.0f%%",
                  new SimpleDateFormat("HH:mm:ss.SSS").format(new Date()), cpuIndex, 100 * (1 - idleRatio),
                  100 * (1 - IDLE_THRESHOLD));
            log.warn(message);
            if (warmupTest.getAsBoolean()) {
               errorHandler.accept(new BenchmarkExecutionException(message));
               idle = Long.MAX_VALUE;
            }
         }
      }
      if (prevIdle != Long.MAX_VALUE) {
         idleTime[cpuIndex] = idle;
      }
   }

   public void start() {
      if (thread != null) {
         thread.start();
      }
   }

   public void stop() {
      running = false;
   }

   public synchronized void notifyPhaseStart(String name) {
      if (nCpu <= 0)
         return;
      PhaseRecord record = new PhaseRecord(System.nanoTime(), new long[nCpu]);
      if (readProcStat(parts -> {
         int cpuIndex = Integer.parseInt(parts[0], 3, parts[0].length(), 10);
         record.cpuIdle[cpuIndex] = Long.parseLong(parts[4]);
      })) {
         phaseStart.putIfAbsent(name, record);
      }
   }

   public synchronized void notifyPhaseEnd(String name) {
      if (nCpu <= 0) {
         return;
      }
      PhaseRecord start = phaseStart.get(name);
      if (start == null || phaseUsage.containsKey(name)) {
         return;
      }
      long now = System.nanoTime();
      SumMin acc = new SumMin();
      if (readProcStat(parts -> {
         int cpuIndex = Integer.parseInt(parts[0], 3, parts[0].length(), 10);
         long idle = Long.parseLong(parts[4]);
         long diff = idle - start.cpuIdle[cpuIndex];
         acc.sum += diff;
         acc.min = Math.min(acc.min, diff);
      })) {
         double idleCores = (double) (TICK_NANOS * acc.sum) / (now - start.timestamp);
         double minIdleRatio = (double) (TICK_NANOS * acc.min) / (now - start.timestamp);
         phaseUsage.put(name, String.format("%.1f%% (%.1f/%d cores), 1 core max %.1f%%",
               100 - 100 * idleCores / nCpu, nCpu - idleCores, nCpu, 100 - 100 * minIdleRatio));
      }
   }

   public String getCpuUsage(String name) {
      return phaseUsage.get(name);
   }

   private static class SumMin {
      long sum;
      long min = Long.MAX_VALUE;
   }

   private static class PhaseRecord {
      final long timestamp;
      final long[] cpuIdle;

      private PhaseRecord(long timestamp, long[] cpuIdle) {
         this.timestamp = timestamp;
         this.cpuIdle = cpuIdle;
      }
   }
}
