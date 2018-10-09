package io.sailrocket.clustering.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import io.sailrocket.api.config.Benchmark;
import io.sailrocket.core.parser.BenchmarkParser;
import io.sailrocket.core.parser.ParserException;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class PersistenceUtil {
   private static final Logger log = LoggerFactory.getLogger(PersistenceUtil.class);

   public static byte[] serialize(Benchmark benchmark) {
      ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
      try (ObjectOutputStream outputStream = new ObjectOutputStream(byteArrayOutputStream)) {
         outputStream.writeObject(benchmark);
      } catch (IOException e) {
         log.error("Serialization failed", e);
         return null;
      }
      return byteArrayOutputStream.toByteArray();
   }

   public static Benchmark deserialize(byte[] bytes) {
      try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
         return (Benchmark) input.readObject();
      } catch (IOException | ClassNotFoundException | ClassCastException e) {
         log.error("Deserialization failed", e);
         return null;
      }
   }

   public static void store(Benchmark benchmark, Path dir) {
        byte[] bytes = serialize(benchmark);
        if (bytes != null) {
            Path path = dir.resolve(benchmark.name() + ".serialized");
            try {
                Files.write(path, bytes);
                log.info("Stored benchmark '{}' in {}", benchmark.name(), path);
            } catch (IOException e) {
                log.error(e, "Failed to persist benchmark {} to {}", benchmark.name(), path);
            }
        }
        if (benchmark.source() != null) {
            Path path = dir.resolve(benchmark.name() + ".yaml");
            try {
                Files.write(path, benchmark.source().getBytes(StandardCharsets.UTF_8));
                log.info("Stored benchmark '{}' in {}", benchmark.name(), path);
            } catch (IOException e) {
                log.error(e, "Failed to persist benchmark {} to {}", benchmark.name(), path);
            }
        }
   }

   public static Benchmark load(Path file) {
       String filename = file.getFileName().toString();
       if (filename.endsWith(".yaml")) {
           try {
               String source = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
               Benchmark benchmark = BenchmarkParser.instance().buildBenchmark(source);
               log.info("Loaded benchmark '{}' from {}", benchmark.name(), file);
               return benchmark;
           } catch (IOException e) {
               log.error(e, "Cannot read file {}", file);
           } catch (ParserException e) {
               log.error(e, "Cannot parser file {}", file);
           }
       } else if (filename.endsWith(".serialized")) {
           try {
               Benchmark benchmark = deserialize(Files.readAllBytes(file));
               if (benchmark != null) {
                   log.info("Loaded benchmark '{}' from {}", benchmark.name(), file);
                   return benchmark;
               }
           } catch (IOException e) {
               log.error(e, "Cannot read file {}", file);
           }
       } else {
           log.warn("Unknown benchmark file format: {}", file);
       }
       return null;
   }
}
