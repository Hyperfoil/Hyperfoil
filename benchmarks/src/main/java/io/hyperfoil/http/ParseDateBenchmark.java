package io.hyperfoil.http;

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(2)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
public class ParseDateBenchmark {

   private String[] sequences;

   @Setup
   public void setup() {
      sequences = new String[] { "Tue, 29 Oct 2024 16:56:32 UTC", "Tue, 29 Oct 2024 16:56:32 GMT",
            "Tue, 29 Oct 2024 16:56:32 CET" };
   }

   @Benchmark
   public void parseDates(Blackhole bh) {
      for (String seq : sequences) {
         bh.consume(HttpUtil.parseDate(seq));
      }
   }
}
