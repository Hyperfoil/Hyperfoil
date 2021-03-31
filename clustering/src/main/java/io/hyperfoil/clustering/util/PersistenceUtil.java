package io.hyperfoil.clustering.util;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.BenchmarkData;
import io.hyperfoil.core.parser.BenchmarkParser;
import io.hyperfoil.core.parser.ParserException;
import io.hyperfoil.core.util.Util;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.message.FormattedMessage;

public class PersistenceUtil {
   private static final Logger log = LogManager.getLogger(PersistenceUtil.class);

   public static void store(Benchmark benchmark, Path dir) {
      try {
         byte[] bytes = Util.serialize(benchmark);
         Path path = dir.resolve(benchmark.name() + ".serialized");
         try {
            Files.write(path, bytes);
            log.info("Stored benchmark '{}' in {}", benchmark.name(), path);
         } catch (IOException e) {
            log.error(new FormattedMessage("Failed to persist benchmark {} to {}", benchmark.name(), path), e);
         }
      } catch (IOException e) {
         log.error("Failed to serialize", e);
      }
      if (benchmark.source() != null) {
         if (!dir.toFile().exists()) {
            if (!dir.toFile().mkdirs()) {
               log.error("Failed to create directory {}", dir);
            }
         }
         Path path = dir.resolve(benchmark.name() + ".yaml");
         try {
            Files.write(path, benchmark.source().getBytes(StandardCharsets.UTF_8));
            log.info("Stored benchmark '{}' in {}", benchmark.name(), path);
         } catch (IOException e) {
            log.error(new FormattedMessage("Failed to persist benchmark {} to {}", benchmark.name(), path), e);
         }
         Path dataDirPath = dir.resolve(benchmark.name() + ".data");
         File dataDir = dataDirPath.toFile();
         if (dataDir.exists()) {
            // Make sure the directory is empty
            //noinspection ConstantConditions
            for (File file : dataDir.listFiles()) {
               if (file.delete()) {
                  log.warn("Could not delete old file {}", file);
               }
            }
            if (benchmark.files().isEmpty()) {
               //noinspection ResultOfMethodCallIgnored
               dataDir.delete();
            }
         } else if (!dataDir.exists() && !benchmark.files().isEmpty()) {
            if (!dataDir.mkdir()) {
               log.error("Couldn't create data dir {}", dataDir);
               return;
            }
         }
         try {
            PersistedBenchmarkData.store(benchmark.files(), dataDirPath);
         } catch (IOException e) {
            log.error("Couldn't persist files for benchmark " + benchmark.name(), e);
         }
      }
   }

   public static Benchmark load(Path file) {
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
            String source = Files.readString(file);
            Benchmark benchmark = BenchmarkParser.instance().buildBenchmark(source, data);
            log.info("Loaded benchmark '{}' from {}", benchmark.name(), file);
            return benchmark;
         } catch (IOException e) {
            log.error("Cannot read file " + file, e);
         } catch (ParserException e) {
            log.error("Cannot parser file " + file, e);
         }
      } else if (filename.endsWith(".serialized")) {
         try {
            Benchmark benchmark = Util.deserialize(Files.readAllBytes(file));
            if (benchmark != null) {
               log.info("Loaded benchmark '{}' from {}", benchmark.name(), file);
               return benchmark;
            }
         } catch (Exception e) {
            log.info("Cannot load serialized benchmark from {} (likely a serialization issue, see traces for details)", file);
            log.trace("Cannot read file " + file, e);
            return null;
         }
      } else if (file.toFile().isDirectory() && filename.endsWith(".data")) {
         log.debug("Ignoring directory {}", filename);
      } else {
         log.warn("Unknown benchmark file format: {}", file);
      }
      return null;
   }
}
