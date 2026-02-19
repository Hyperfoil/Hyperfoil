package io.hyperfoil.core.util.watchdog;

import java.util.ArrayList;
import java.util.List;

public class MockProcStatReader implements ProcStatReader {

   private final List<List<String>> fileStates;
   private int index = 0;

   @SafeVarargs
   public MockProcStatReader(List<String>... fileStates) {
      this.fileStates = new ArrayList<>();
      // there is a read on the constructor for nCpu
      this.fileStates.add(List.of(
            "cpu  0 0 0 0 0 0 0 0 0 0",
            "cpu0 0 0 0 0 0 0 0 0 0 0"));
      for (List<String> fileState : fileStates) {
         // if you need to test more, change the line above
         if (fileState.size() != 2) {
            throw new IllegalArgumentException("Mock currently only supports 1 CPU core.");
         }
      }
      this.fileStates.addAll(List.of(fileStates));
   }

   @Override
   public List<String> readLines() {
      List<String> currentState = fileStates.get(index);
      index++;
      return currentState;
   }
}