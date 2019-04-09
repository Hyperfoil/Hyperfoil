package io.hyperfoil.clustering;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import io.hyperfoil.api.config.BenchmarkData;

public class RequestBenchmarkData implements BenchmarkData {
   private final Map<String, byte[]> files = new HashMap<>();

   public void addFile(String name, byte[] bytes) {
      files.put(Objects.requireNonNull(name), Objects.requireNonNull(bytes));
   }

   @Override
   public InputStream readFile(String file) {
      byte[] bytes = files.get(file);
      if (bytes == null) {
         return null;
      }
      return new ByteArrayInputStream(bytes);
   }

   @Override
   public Map<String, byte[]> files() {
      return files;
   }
}
