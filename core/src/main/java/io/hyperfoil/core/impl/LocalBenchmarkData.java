package io.hyperfoil.core.impl;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import io.hyperfoil.api.config.BenchmarkData;
import io.hyperfoil.api.config.BenchmarkDefinitionException;

public class LocalBenchmarkData implements BenchmarkData {
   private final Path benchmarkPath;
   private final Map<String, byte[]> readFiles = new HashMap<>();

   public LocalBenchmarkData(Path benchmarkPath) {
      this.benchmarkPath = benchmarkPath;
   }

   @Override
   public InputStream readFile(String file) {
      Path path = Paths.get(file);
      if (!path.isAbsolute()) {
         path = benchmarkPath.getParent().resolve(file);
      }
      try {
         readFiles.put(file, Files.readAllBytes(path));
      } catch (IOException e) {
         throw new BenchmarkDefinitionException("Local file " + file + " (" + path.toAbsolutePath() + ") cannot be read.", e);
      }
      try {
         return new FileInputStream(path.toFile());
      } catch (FileNotFoundException e) {
         throw new BenchmarkDefinitionException("Local file " + file + " (" + path.toAbsolutePath() + ") was not found.", e);
      }
   }

   @Override
   public Map<String, byte[]> files() {
      return readFiles;
   }
}
