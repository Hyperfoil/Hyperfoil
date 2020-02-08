package io.hyperfoil.benchmark.standalone;

import io.hyperfoil.cli.commands.Wrk;
import io.hyperfoil.test.Benchmark;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@Category(Benchmark.class)
public class WrkTest extends BaseBenchmarkTestCase {

   public WrkTest() {
      this.servedRatio = 0.9;
      this.unservedDelay = 2000;
   }

   @Test
   public void test() {
      Wrk.main(new String[]{ "-c", "10", "-d", "5s", "--latency", "--timeout", "1s", "localhost:" + server.actualPort() + "/foo/bar" });
   }
}
