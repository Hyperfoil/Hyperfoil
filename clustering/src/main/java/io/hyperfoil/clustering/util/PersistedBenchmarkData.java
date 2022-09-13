package io.hyperfoil.clustering.util;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

import io.hyperfoil.api.config.BenchmarkData;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class PersistedBenchmarkData implements BenchmarkData {
   private static final Logger log = LogManager.getLogger(PersistedBenchmarkData.class);
   private final Path dir;

   public static void store(Map<String, byte[]> files, Path dir) throws IOException {
      for (Map.Entry<String, byte[]> entry : files.entrySet()) {
         Files.write(dir.resolve(BenchmarkData.sanitize(entry.getKey())), entry.getValue());
      }
   }

   public PersistedBenchmarkData(Path dir) {
      this.dir = dir;
   }

   @Override
   public InputStream readFile(String file) {
      String sanitized = BenchmarkData.sanitize(file);
      try {
         return new FileInputStream(dir.resolve(sanitized).toFile());
      } catch (FileNotFoundException e) {
         throw new MissingFileException("Cannot load file " + file + "(" + sanitized + ") from directory " + dir, file, e);
      }
   }

   @Override
   public Map<String, byte[]> files() {
      if (!dir.toFile().exists() || !dir.toFile().isDirectory()) {
         return Collections.emptyMap();
      }
      try {
         return Files.list(dir).collect(Collectors.toMap(path -> path.getFileName().toString(), (Path path) -> {
            try {
               return Files.readAllBytes(path);
            } catch (IOException e) {
               log.error("Cannot read file " + path, e);
               throw new RuntimeException(e);
            }
         }));
      } catch (IOException e) {
         throw new RuntimeException("Cannot list directory " + dir, e);
      }
   }

}
