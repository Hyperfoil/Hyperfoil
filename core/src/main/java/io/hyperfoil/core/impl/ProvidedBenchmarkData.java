package io.hyperfoil.core.impl;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.hyperfoil.api.config.BenchmarkData;

public class ProvidedBenchmarkData implements BenchmarkData {
   public final Set<String> ignoredFiles = new HashSet<>();
   public final Map<String, byte[]> files = new HashMap<>();

   public ProvidedBenchmarkData() {
   }

   public ProvidedBenchmarkData(Map<String, byte[]> files) {
      this.files.putAll(files);
   }

   @Override
   public InputStream readFile(String file) {
      if (ignoredFiles.contains(file) || ignoredFiles.contains(BenchmarkData.sanitize(file))) {
         return ByteArrayInputStream.nullInputStream();
      }
      byte[] bytes = files.get(file);
      if (bytes != null) {
         return new ByteArrayInputStream(bytes);
      }
      throw new MissingFileException(file);
   }

   @Override
   public Map<String, byte[]> files() {
      return files;
   }

}
