package io.hyperfoil.core.util.watchdog;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

public class CpuWatchdogTest {

   @Test
   public void testCpuWatchdogFalsePositiveOn100msTicks() throws Exception {
      int period = 1000;
      System.setProperty("io.hyperfoil.cpu.watchdog.period", String.valueOf(period));
      AtomicBoolean errorTriggered = new AtomicBoolean(false);
      MockProcStatReader mockReader = new MockProcStatReader(
            List.of(
                  "cpu  0 0 0 100 0 0 0 0 0 0",
                  "cpu0 0 0 0 100 0 0 0 0 0 0"),
            List.of(
                  "cpu  0 0 0 110 0 0 0 0 0 0",
                  "cpu0 0 0 0 110 0 0 0 0 0 0"));
      CpuWatchdog watchdog = new CpuWatchdog(
            err -> errorTriggered.set(true),
            () -> true,
            mockReader);
      Thread t = new Thread(watchdog);
      t.start();
      Thread.sleep(period + (period / 2));
      watchdog.stop();
      t.join();
      assertFalse(errorTriggered.get(), "The watchdog falsely triggered an error on an idle CPU.");
   }

   @Test
   public void testCpuWatchdogCorrectlyDetectsHighLoad() throws Exception {
      int period = 1000;
      System.setProperty("io.hyperfoil.cpu.watchdog.period", String.valueOf(period));
      AtomicBoolean errorTriggered = new AtomicBoolean(false);
      MockProcStatReader mockReader = new MockProcStatReader(
            List.of(
                  "cpu  0 0 0 100 0 0 0 0 0 0",
                  "cpu0 0 0 0 100 0 0 0 0 0 0"),
            List.of(
                  "cpu  90 0 0 110 0 0 0 0 0 0",
                  "cpu0 90 0 0 110 0 0 0 0 0 0"));
      CpuWatchdog watchdog = new CpuWatchdog(
            err -> errorTriggered.set(true),
            () -> true,
            mockReader);
      Thread t = new Thread(watchdog);
      t.start();
      Thread.sleep(period + (period / 2));
      watchdog.stop();
      t.join();
      assertTrue(errorTriggered.get(), "The watchdog should trigger an error when CPU load is genuinely high.");
   }
}
