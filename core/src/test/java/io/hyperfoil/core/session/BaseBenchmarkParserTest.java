package io.hyperfoil.core.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.core.parser.BenchmarkParser;
import io.hyperfoil.core.parser.ParserException;
import io.hyperfoil.core.test.TestUtil;
import io.hyperfoil.impl.Util;

public abstract class BaseBenchmarkParserTest {

   protected Benchmark loadScenario(String filePath) {
      InputStream stream = null;
      try {

         if (Files.exists(Path.of(filePath))) {
            stream = new FileInputStream(filePath);
         } else {
            stream = getClass().getClassLoader().getResourceAsStream(filePath);
         }
         Benchmark benchmark = loadBenchmark(stream);
         // Serialization here is solely for the purpose of asserting serializability for all the components
         byte[] bytes = Util.serialize(benchmark);
         assertThat(bytes).isNotNull();
         return benchmark;
      } catch (IOException | ParserException e) {
         throw new AssertionError(e);
      } finally {
         if (stream != null) {
            try {
               stream.close();
            } catch (IOException e) {
               throw new RuntimeException(e);
            }
         }
      }
   }

   protected Benchmark loadBenchmark(InputStream config) throws IOException, ParserException {
      return BenchmarkParser.instance().buildBenchmark(config, TestUtil.benchmarkData(), Collections.emptyMap());
   }

   protected Benchmark loadBenchmark(InputStream config, Map<String, String> arguments) throws IOException, ParserException {
      return BenchmarkParser.instance().buildBenchmark(config, TestUtil.benchmarkData(), arguments);
   }
}
