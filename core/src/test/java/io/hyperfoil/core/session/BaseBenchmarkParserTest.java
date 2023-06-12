package io.hyperfoil.core.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.core.parser.BenchmarkParser;
import io.hyperfoil.core.parser.ParserException;
import io.hyperfoil.core.test.TestUtil;
import io.hyperfoil.impl.Util;

public abstract class BaseBenchmarkParserTest {

   protected Benchmark loadScenario(String name) {
      try {
         InputStream config = getClass().getClassLoader().getResourceAsStream(name);
         Benchmark benchmark = loadBenchmark(config);
         // Serialization here is solely for the purpose of asserting serializability for all the components
         byte[] bytes = Util.serialize(benchmark);
         assertThat(bytes).isNotNull();
         return benchmark;
      } catch (IOException | ParserException e) {
         throw new AssertionError(e);
      }
   }

   protected Benchmark loadBenchmark(InputStream config) throws IOException, ParserException {
      return BenchmarkParser.instance().buildBenchmark(config, TestUtil.benchmarkData(), Collections.emptyMap());
   }

}
