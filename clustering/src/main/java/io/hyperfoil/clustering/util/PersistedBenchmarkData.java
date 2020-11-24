package io.hyperfoil.clustering.util;

import java.io.File;
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
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class PersistedBenchmarkData implements BenchmarkData {
   private static final Logger log = LoggerFactory.getLogger(PersistedBenchmarkData.class);
   private final Path dir;

   public static void store(Map<String, byte[]> files, Path dir) throws IOException {
      for (Map.Entry<String, byte[]> entry : files.entrySet()) {
         Files.write(dir.resolve(sanitize(entry.getKey())), entry.getValue());
      }
   }

   public PersistedBenchmarkData(Path dir) {
      this.dir = dir;
   }

   @Override
   public InputStream readFile(String file) {
      try {
         return new FileInputStream(dir.resolve(sanitize(file)).toFile());
      } catch (FileNotFoundException e) {
         log.error("Cannot load file {} from directory {}", e, file, dir);
         return null;
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
               log.error("Cannot read file {}", e, path);
               throw new RuntimeException(e);
            }
         }));
      } catch (IOException e) {
         throw new RuntimeException("Cannot list directory " + dir, e);
      }
   }

   public static String sanitize(String file) {
      return file.replace(File.separatorChar, '_').replace(File.pathSeparatorChar, '_');
   }
}
