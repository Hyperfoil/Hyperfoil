package io.hyperfoil.core.util.watchdog;

import java.io.IOException;
import java.util.List;

public class MockProcStatReader implements ProcStatReader {

   private List<String> statRead1 = List.of(
         // CPU has 100 idle ticks
         "cpu  0 0 0 100 0 0 0 0 0 0",
         "cpu0 0 0 0 100 0 0 0 0 0 0");
   private List<String> statRead2 = List.of(
         // called after io.hyperfoil.cpu.watchdog.period
         // after io.hyperfoil.cpu.watchdog.period we have 110 idle ticks
         "cpu  0 0 0 110 0 0 0 0 0 0",
         "cpu0 0 0 0 110 0 0 0 0 0 0");

   private int count = 0;

   @Override
   public List<String> readLines() throws IOException {
      count++;
      // 1st read: constructor
      // 2nd read: first loop iteration
      // 3rd read: after sleeping 1000ms
      if (count < 3) {
         return statRead1;
      }
      return statRead2;
   }
}
