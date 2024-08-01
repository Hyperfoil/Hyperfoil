package io.hyperfoil.benchmark.standalone;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.BenchmarkData;
import io.hyperfoil.benchmark.BaseBenchmarkTest;
import io.hyperfoil.core.impl.LocalSimulationRunner;
import io.hyperfoil.core.parser.BenchmarkParser;
import io.hyperfoil.core.parser.ParserException;

@Tag("io.hyperfoil.test.Benchmark")
public class IterationsTest extends BaseBenchmarkTest {

   @Test
   public void test() throws IOException, ParserException {
      try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("IterationsTest.hf.yaml")) {
         Benchmark benchmark = BenchmarkParser.instance().buildBenchmark(
               inputStream, BenchmarkData.EMPTY, Map.of("PORT", String.valueOf(httpServer.actualPort())));
         new LocalSimulationRunner(benchmark).run();
      }
   }
}
