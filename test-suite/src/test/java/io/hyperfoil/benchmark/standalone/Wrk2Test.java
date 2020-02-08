package io.hyperfoil.benchmark.standalone;

import io.hyperfoil.cli.commands.Wrk2;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import io.hyperfoil.test.Benchmark;

@Category(Benchmark.class)
public class Wrk2Test extends BaseBenchmarkTestCase {

   public Wrk2Test() {
      this.servedRatio = 0.9;
      this.unservedDelay = 2000;
   }

   @Test
   public void test() {
      Wrk2.main(new String[]{ "-c", "10", "-d", "5s", "-R", "20", "--latency", "--timeout", "1s", "localhost:" + server.actualPort() + "/foo/bar" });
   }
}
