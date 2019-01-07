package io.sailrocket.benchmark.standalone;

import java.io.IOException;
import java.io.InputStream;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import io.sailrocket.api.config.Benchmark;
import io.sailrocket.core.impl.LocalSimulationRunner;
import io.sailrocket.core.parser.BenchmarkParser;
import io.sailrocket.core.parser.ParserException;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
@Category(io.sailrocket.test.Benchmark.class)
public class IterationsTest extends BaseBenchmarkTestCase {

   @Test
   public void test() throws IOException, ParserException {
      InputStream inputStream = getClass().getClassLoader().getResourceAsStream("IterationsTest.yaml");
      Benchmark benchmark = BenchmarkParser.instance().buildBenchmark(inputStream);

      new LocalSimulationRunner(benchmark).run();
   }
}
