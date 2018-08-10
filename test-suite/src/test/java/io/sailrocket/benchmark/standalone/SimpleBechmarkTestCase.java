package io.sailrocket.benchmark.standalone;

import io.sailrocket.test.Benchmark;
import io.sailrocket.core.BenchmarkImpl;

import org.junit.Test;
import org.junit.experimental.categories.Category;


@Category(Benchmark.class)
public class SimpleBechmarkTestCase extends BaseBenchmarkTestCase {
    @Test
    public void runSimpleBenchmarkTest() throws Exception {
        BenchmarkImpl benchmark = new BenchmarkImpl("Simple Benchmark");
        benchmark
              .agents("localhost")
              .users(10)
              .endpoint("http://localhost:8080/")
              .run();
    }
}
