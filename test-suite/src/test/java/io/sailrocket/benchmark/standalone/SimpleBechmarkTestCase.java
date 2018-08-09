package io.sailrocket.benchmark.standalone;

import io.sailrocket.core.BenchmarkImpl;
import io.vertx.ext.unit.TestContext;
import org.junit.Before;


public class SimpleBechmarkTestCase extends BaseBenchmarkTestCase {

    @Before
    public void before(TestContext ctx) {
        simpleBenchmark = new SimpleBechmarkTestCase.SimpleBenchmark("Simple Benchmark");
        super.before(ctx);
    }

    class SimpleBenchmark extends BenchmarkImpl {


        public SimpleBenchmark(String name) {
            super(name);
            agents("localhost").users(10).endpoint("http://localhost:8080/");
        }
    }
}
