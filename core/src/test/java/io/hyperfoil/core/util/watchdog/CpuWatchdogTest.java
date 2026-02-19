package io.hyperfoil.core.util.watchdog;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

public class CpuWatchdogTest {

   @Test
   public void testCpuWatchdogFalsePositiveOn100msTicks() throws Exception {
      int period = 1000;
      System.setProperty("io.hyperfoil.cpu.watchdog.period", String.valueOf(period));
      AtomicBoolean errorTriggered = new AtomicBoolean(false);
      MockProcStatReader mockReader = new MockProcStatReader();
      CpuWatchdog watchdog = new CpuWatchdog(
            err -> errorTriggered.set(true),
            () -> true,
            mockReader);
      Thread t = new Thread(watchdog);
      t.start();
      // When the watchdog wakes up after your (io.hyperfoil.cpu.watchdog.period) 1000ms sleep, it runs this line of code: double idleRatio = (double) (TICK_NANOS * (idle - prevIdle)) / (now - lastTimestamp);
      // TICK_NANOS: The code hardcodes this to 10,000,000 (10 milliseconds).
      // idle - prevIdle: The delta from your mock is 10 ticks (110 - 100).
      // now - lastTimestamp: The time your thread slept, which is roughly 1,000,000,000 nanoseconds (1000 milliseconds).

      Thread.sleep(period + (period / 2));
      watchdog.stop();
      t.join();

      // The Math:
      // Calculates assumed idle time: 10,000,000 ns * 10 ticks = 100,000,000 ns (100 milliseconds).
      // Calculates the ratio: 100,000,000 ns / 1,000,000,000 ns = 0.10.

      // The False Positive
      // The math resulted in an idleRatio of 0.10 (meaning it thinks the CPU was only 10% idle, or 90% busy).
      // Because 0.10 is lower than the default IDLE_THRESHOLD of 0.20 (20% idle), the watchdog panics! It logs the warning and calls your errorHandler, which sets errorTriggered to true.
      assertFalse(errorTriggered.get(), "The original code falsely triggers an error due to tick mismatch.");
   }
}
