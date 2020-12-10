package io.hyperfoil.api.config;

import java.io.InputStream;
import java.util.Collections;
import java.util.Map;

public interface BenchmarkData {
   BenchmarkData EMPTY = new BenchmarkData() {
      @Override
      public InputStream readFile(String file) {
         throw new BenchmarkDefinitionException("Cannot load file " + file + " (file set is empty).");
      }

      @Override
      public Map<String, byte[]> files() {
         return Collections.emptyMap();
      }
   };

   InputStream readFile(String file);

   Map<String, byte[]> files();
}
