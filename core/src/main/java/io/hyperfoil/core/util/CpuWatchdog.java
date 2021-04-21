package io.hyperfoil.core.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.hyperfoil.api.BenchmarkExecutionException;
import io.hyperfoil.internal.Properties;

public class CpuWatchdog implements Runnable {
   private static final Logger log = LogManager.getLogger(CpuWatchdog.class);
   private static final Path PROC_STAT = Path.of("/proc/stat");
   private static final long PERIOD = Properties.getLong(Properties.CPU_WATCHDOG_PERIOD, 5000);
   private static final double IDLE_THRESHOLD = Double.parseDouble(Properties.get(Properties.CPU_WATCHDOG_IDLE_THRESHOLD, "0.2"));
   // On most architectures the tick is defined as 1/100 of second (10 ms)
   // where the value of 100 can be obtained using sysconf(_SC_CLK_TCK)
   private static final long TICK_NANOS = Properties.getLong("io.hyperfoil.clock.tick.nanos", 10_000_000);

   private final Consumer<Throwable> errorHandler;
   private final Thread thread;
   private long[] idleTime = new long[8];
   private volatile boolean running = true;

   public CpuWatchdog(Consumer<Throwable> errorHandler) {
      thread = new Thread(this, "cpu-watchdog");
      thread.setDaemon(true);
      this.errorHandler = errorHandler;
   }

   public void run() {
      long lastTimestamp = System.nanoTime();
      long now = lastTimestamp;
      while (running) {
         try {
            List<String> lines = Files.readAllLines(PROC_STAT);
            for (String line : lines) {
               if (!line.startsWith("cpu")) continue;
               String[] parts = line.split(" ");
               // ignore overall stats
               if ("cpu".equals(parts[0])) continue;
               // weird format?
               if (parts.length < 5) continue;
               try {
                  int cpuIndex = Integer.parseInt(parts[0], 3, parts[0].length(), 10);
                  long idle = Long.parseLong(parts[4]);

                  if (cpuIndex >= idleTime.length) {
                     idleTime = Arrays.copyOf(idleTime, Math.max(2 * idleTime.length, cpuIndex + 1));
                  }
                  long prevIdle = idleTime[cpuIndex];
                  if (prevIdle != 0 && prevIdle != Long.MAX_VALUE && lastTimestamp != now) {
                     double idleRatio = (double) (TICK_NANOS * (idle - prevIdle)) / (now - lastTimestamp);
                     if (idleRatio < IDLE_THRESHOLD) {
                        String message = String.format("CPU %d was used for %.0f%% which is more than the threshold of %.0f%%",
                              cpuIndex, 100 * (1 - idleRatio), 100 * (1 - IDLE_THRESHOLD));
                        log.warn(message);
                        errorHandler.accept(new BenchmarkExecutionException(message));
                        idle = Long.MAX_VALUE;
                     }
                  }
                  if (prevIdle != Long.MAX_VALUE) {
                     idleTime[cpuIndex] = idle;
                  }
               } catch (NumberFormatException e) {
                  log.error("CPU watchdog cannot parse stats, terminating.");
                  return;
               }
            }
         } catch (IOException e) {
            log.error("Failed reading " + PROC_STAT + ", CPU watchog is terminating.", e);
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

   public void start() {
      File stat = PROC_STAT.toFile();
      if (!stat.exists() || !stat.isFile() || !stat.canRead()) {
         log.warn("Not starting CPU watchdog as {} is not available (exists: {}, file: {}, readable: {})",
               PROC_STAT, stat.exists(), stat.isFile(), stat.canRead());
         return;
      }
      thread.start();
   }

   public void stop() {
      running = false;
   }
}
