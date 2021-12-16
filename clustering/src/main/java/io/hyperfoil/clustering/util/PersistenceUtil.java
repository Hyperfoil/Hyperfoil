package io.hyperfoil.clustering.util;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import io.hyperfoil.api.config.BenchmarkData;
import io.hyperfoil.api.config.BenchmarkSource;
import io.hyperfoil.core.parser.BenchmarkParser;
import io.hyperfoil.core.parser.ParserException;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.message.FormattedMessage;

public class PersistenceUtil {
   private static final Logger log = LogManager.getLogger(PersistenceUtil.class);

   public static void store(BenchmarkSource source, Path dir) {
      if (!dir.toFile().exists()) {
         if (!dir.toFile().mkdirs()) {
            log.error("Failed to create directory {}", dir);
         }
      }
      Path path = dir.resolve(source.name + ".yaml");
      try {
         Files.write(path, source.yaml.getBytes(StandardCharsets.UTF_8));
         log.info("Stored benchmark '{}' in {}", source.name, path);
      } catch (IOException e) {
         log.error(new FormattedMessage("Failed to persist benchmark {} to {}", source.name, path), e);
      }
      Path dataDirPath = dir.resolve(source.name + ".data");
      File dataDir = dataDirPath.toFile();
      Map<String, byte[]> files = source.data.files();
      if (dataDir.exists()) {
         if (!dataDir.isDirectory()) {
            if (!dataDir.delete() || !dataDir.mkdir()) {
               log.error("Couldn't delete/create data dir {}", dataDir);
               return;
            }
         }
         // Make sure the directory is empty
         //noinspection ConstantConditions
         for (File file : dataDir.listFiles()) {
            if (file.delete()) {
               log.warn("Could not delete old file {}", file);
            }
         }
         if (files.isEmpty()) {
            //noinspection ResultOfMethodCallIgnored
            dataDir.delete();
         }
      } else if (!dataDir.exists() && !files.isEmpty()) {
         if (!dataDir.mkdir()) {
            log.error("Couldn't create data dir {}", dataDir);
            return;
         }
      }
      try {
         PersistedBenchmarkData.store(files, dataDirPath);
      } catch (IOException e) {
         log.error("Couldn't persist files for benchmark " + source.name, e);
      }
   }

   public static BenchmarkSource load(Path file) {
      String filename = file.getFileName().toString();
      if (filename.endsWith(".yaml")) {
         BenchmarkData data = BenchmarkData.EMPTY;
         String dataDirName = filename.substring(0, filename.length() - 5) + ".data";
         Path dataDirPath = file.getParent().resolve(dataDirName);
         File dataDir = dataDirPath.toFile();
         if (dataDir.exists()) {
            if (dataDir.isDirectory()) {
               data = new PersistedBenchmarkData(dataDirPath);
            } else {
               log.error("Expected data dir {} to be a directory!", dataDirName);
            }
         }
         try {
            BenchmarkSource source = BenchmarkParser.instance().createSource(Files.readString(file), data);
            log.info("Loaded benchmark from {}", file);
            return source;
         } catch (IOException e) {
            log.error("Cannot read file " + file, e);
         } catch (ParserException e) {
            log.error("Cannot parse file " + file, e);
         }
      } else if (filename.endsWith(".serialized")) {
         log.debug("Serialized benchmarks are not used anymore, ignoring {}", filename);
      } else if (file.toFile().isDirectory() && filename.endsWith(".data")) {
         log.debug("Ignoring directory {}", filename);
      } else {
         log.warn("Unknown benchmark file format: {}", file);
      }
      return null;
   }
}
