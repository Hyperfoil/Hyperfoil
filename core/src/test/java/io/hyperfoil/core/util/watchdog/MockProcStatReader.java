package io.hyperfoil.core.util.watchdog;

import java.util.ArrayList;
import java.util.List;

public class MockProcStatReader implements ProcStatReader {

   private final List<List<String>> fileStates;
   private int index = 0;

   @SafeVarargs
   public MockProcStatReader(List<String>... fileStates) {
      this.fileStates = new ArrayList<>();

      // Generate the initial read for the CpuWatchdog constructor dynamically
      // based on the layout of the first provided state.
      if (fileStates.length > 0) {
         List<String> initialState = new ArrayList<>();
         for (String line : fileStates[0]) {
            if (line.startsWith("cpu")) {
               String[] parts = line.split("\\s+");
               initialState.add(parts[0] + " 0 0 0 0 0 0 0 0 0 0");
            } else {
               initialState.add(line);
            }
         }
         this.fileStates.add(initialState);
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
