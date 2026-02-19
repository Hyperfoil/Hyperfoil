package io.hyperfoil.core.util.watchdog;

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
   private static final double IDLE_THRESHOLD = Double
         .parseDouble(Properties.get(Properties.CPU_WATCHDOG_IDLE_THRESHOLD, "0.2"));

   private final Consumer<Throwable> errorHandler;
   private final Thread thread;
   private final BooleanSupplier warmupTest;
   private final int nCpu;
   private final long[] idleTime;
   private final long[] totalTime;
   private volatile boolean running = true;
   private final Map<String, PhaseRecord> phaseStart = new HashMap<>();
   private final Map<String, String> phaseUsage = new HashMap<>();
   private final ProcStatReader statReader;
   private final long cpuWatchDocPeriod;

   public CpuWatchdog(Consumer<Throwable> errorHandler, BooleanSupplier warmupTest) {
      this(errorHandler, warmupTest, () -> Files.readAllLines(PROC_STAT));
   }

   public CpuWatchdog(Consumer<Throwable> errorHandler, BooleanSupplier warmupTest, ProcStatReader statReader) {
      this.errorHandler = errorHandler;
      this.warmupTest = warmupTest;
      this.statReader = statReader;
      this.cpuWatchDocPeriod = Properties.getLong(Properties.CPU_WATCHDOG_PERIOD, 5000);
      File stat = PROC_STAT.toFile();
      if (!stat.exists() || !stat.isFile() || !stat.canRead()) {
         log.warn("Not starting CPU watchdog as {} is not available (exists: {}, file: {}, readable: {})",
               PROC_STAT, stat.exists(), stat.isFile(), stat.canRead());
         thread = null;
         nCpu = 0;
         idleTime = null;
         totalTime = null;
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
      totalTime = new long[nCpu];
   }

   public void run() {
      if (nCpu <= 0) {
         log.error("Illegal number of CPUs");
         return;
      }

      while (running) {
         if (!readProcStat(this::processCpuLine)) {
            log.info("CPU watchdog is terminating.");
            return;
         }
         try {
            Thread.sleep(this.cpuWatchDocPeriod);
         } catch (InterruptedException e) {
            log.error("CPU watchdog thread interrupted, terminating.", e);
            return;
         }
      }
   }

   private boolean readProcStat(Consumer<String[]> consumer) {
      try {
         for (String line : this.statReader.readLines()) {
            if (!line.startsWith("cpu"))
               continue;
            String[] parts = line.split("\\s+");
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
         log.error("CPU watchdog cannot read {}", PROC_STAT, e);
         return false;
      } catch (NumberFormatException e) {
         log.error("CPU watchdog cannot parse stats.", e);
         return false;
      }
   }

   private void processCpuLine(String[] parts) {
      // Remove "cpu" prefix to parse the index
      String cpuStr = parts[0].substring(3);
      int cpuIndex = Integer.parseInt(cpuStr);

      long idle = Long.parseLong(parts[4]);
      long total = 0;
      for (int i = 1; i < parts.length; i++) {
         total += Long.parseLong(parts[i]);
      }

      long prevIdle = idleTime[cpuIndex];
      long prevTotal = totalTime[cpuIndex];

      if (prevTotal != 0 && total > prevTotal) {
         long idleDelta = idle - prevIdle;
         long totalDelta = total - prevTotal;

         double idleRatio = (double) idleDelta / totalDelta;

         if (idleRatio < IDLE_THRESHOLD) {
            String message = String.format("%s | CPU %d was used for %.0f%% which is more than the threshold of %.0f%%",
                  new SimpleDateFormat("HH:mm:ss.SSS").format(new Date()), cpuIndex, 100 * (1 - idleRatio),
                  100 * (1 - IDLE_THRESHOLD));
            log.warn(message);
            if (warmupTest.getAsBoolean()) {
               errorHandler.accept(new BenchmarkExecutionException(message));
               // Prevent rapid-fire logging/exceptions
               idleTime[cpuIndex] = 0;
               totalTime[cpuIndex] = 0;
               return;
            }
         }
      }

      idleTime[cpuIndex] = idle;
      totalTime[cpuIndex] = total;
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
      PhaseRecord record = new PhaseRecord(new long[nCpu], new long[nCpu]);
      if (readProcStat(parts -> {
         String cpuStr = parts[0].substring(3);
         int cpuIndex = Integer.parseInt(cpuStr);
         record.cpuIdle[cpuIndex] = Long.parseLong(parts[4]);

         long total = 0;
         for (int i = 1; i < parts.length; i++) {
            total += Long.parseLong(parts[i]);
         }
         record.cpuTotal[cpuIndex] = total;
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

      SumMin accIdle = new SumMin();
      SumMin accTotal = new SumMin();

      if (readProcStat(parts -> {
         String cpuStr = parts[0].substring(3);
         int cpuIndex = Integer.parseInt(cpuStr);

         long idle = Long.parseLong(parts[4]);
         long idleDiff = idle - start.cpuIdle[cpuIndex];
         accIdle.sum += idleDiff;
         accIdle.min = Math.min(accIdle.min, idleDiff);

         long total = 0;
         for (int i = 1; i < parts.length; i++) {
            total += Long.parseLong(parts[i]);
         }
         long totalDiff = total - start.cpuTotal[cpuIndex];
         accTotal.sum += totalDiff;
         accTotal.min = Math.min(accTotal.min, totalDiff); // Note: For ratio logic, you might want to pair these rather than mix independent mins.
      })) {
         double overallIdleRatio = (double) accIdle.sum / accTotal.sum;
         double minIdleRatio = (double) accIdle.min / (accTotal.sum / nCpu); // Approximation for max core usage

         double idleCores = overallIdleRatio * nCpu;

         phaseUsage.put(name, String.format("%.1f%% (%.1f/%d cores), 1 core max %.1f%%",
               100 * (1 - overallIdleRatio), nCpu - idleCores, nCpu, 100 * (1 - minIdleRatio)));
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
      final long[] cpuIdle;
      final long[] cpuTotal;

      private PhaseRecord(long[] cpuIdle, long[] cpuTotal) {
         this.cpuIdle = cpuIdle;
         this.cpuTotal = cpuTotal;
      }
   }
}
