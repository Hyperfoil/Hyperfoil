package io.hyperfoil.core.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import io.hyperfoil.api.config.BenchmarkData;

public class LocalBenchmarkData implements BenchmarkData {
   protected final Path benchmarkPath;
   protected final Map<String, byte[]> files = new HashMap<>();

   public LocalBenchmarkData(Path benchmarkPath) {
      this.benchmarkPath = benchmarkPath;
   }

   @Override
   public InputStream readFile(String file) {
      byte[] bytes = files.get(file);
      if (bytes == null) {
         Path path = Paths.get(file);
         if (!path.isAbsolute()) {
            if (benchmarkPath == null) {
               throw new MissingFileException(file, "Cannot load relative path " + file, null);
            }
            path = benchmarkPath.getParent().resolve(file);
         }
         try {
            bytes = Files.readAllBytes(path);
            files.put(file, bytes);
         } catch (IOException e) {
            throw new MissingFileException(file, "Local file " + file + " (" + path.toAbsolutePath() + ") cannot be read.", e);
         }
      }
      return new ByteArrayInputStream(bytes);
   }

   @Override
   public Map<String, byte[]> files() {
      return files;
   }
}
