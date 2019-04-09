package io.hyperfoil.core.impl;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import io.hyperfoil.api.config.BenchmarkData;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class LocalBenchmarkData implements BenchmarkData {
   private static final Logger log = LoggerFactory.getLogger(LocalBenchmarkData.class);
   private Map<String, byte[]> readFiles = new HashMap<>();

   @Override
   public InputStream readFile(String file) {
      try {
         readFiles.put(file, Files.readAllBytes(Paths.get(file)));
      } catch (IOException e) {
         log.error("Local file {} cannot be read.", e, file);
         return null;
      }
      try {
         return new FileInputStream(file);
      } catch (FileNotFoundException e) {
         log.error("Local file {} not found.", e, file);
         return null;
      }
   }

   @Override
   public Map<String, byte[]> files() {
      return readFiles;
   }
}
