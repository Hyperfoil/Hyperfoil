package io.hyperfoil.benchmark.standalone;

import java.io.IOException;
import java.io.InputStream;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.BenchmarkData;
import io.hyperfoil.benchmark.BaseBenchmarkTest;
import io.hyperfoil.core.impl.LocalSimulationRunner;
import io.hyperfoil.core.parser.BenchmarkParser;
import io.hyperfoil.core.parser.ParserException;
import io.hyperfoil.impl.Util;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
@Category(io.hyperfoil.test.Benchmark.class)
public class IterationsTest extends BaseBenchmarkTest {

   @Test
   public void test() throws IOException, ParserException {
      InputStream inputStream = getClass().getClassLoader().getResourceAsStream("IterationsTest.hf.yaml");
      String configStr = Util.toString(inputStream)
            .replaceAll("http://localhost:8080", "http://localhost:" + httpServer.actualPort());
      Benchmark benchmark = BenchmarkParser.instance().buildBenchmark(configStr, BenchmarkData.EMPTY);

      new LocalSimulationRunner(benchmark).run();
   }
}
