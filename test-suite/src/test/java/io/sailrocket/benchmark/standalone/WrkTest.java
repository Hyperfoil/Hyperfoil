package io.sailrocket.benchmark.standalone;

import org.aesh.command.parser.CommandLineParserException;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import io.sailrocket.cli.commands.Wrk;
import io.sailrocket.test.Benchmark;

@Category(Benchmark.class)
public class WrkTest extends BaseBenchmarkTestCase {

   public WrkTest() {
      this.servedRatio = 0.9;
      this.unservedDelay = 2000;
   }

   @Test
   public void test() throws CommandLineParserException {
      Wrk.main(new String[] { "-c", "10", "-d", "5s", "-R", "20", "--latency", "--timeout", "1s", "localhost:8080/foo/bar" } );
   }
}
