package io.hyperfoil.example;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.stream.Collectors;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.core.impl.LocalBenchmarkData;
import io.hyperfoil.core.parser.BenchmarkParser;
import io.hyperfoil.util.Util;

@RunWith(Parameterized.class)
public class ValidateExampleTest {
   private final String exampleFile;

   public ValidateExampleTest(String exampleFile) {
      this.exampleFile = exampleFile;
   }

   @Parameterized.Parameters
   public static Collection<Object[]> listExamples() throws IOException {
      return Files.list(Paths.get("examples"))
            .filter(p -> p.toString().endsWith(".hf.yaml"))
            .filter(p -> p.toFile().isFile())
            .map(p -> new Object[]{ p.toString() })
            .collect(Collectors.toList());
   }

   @Test
   public void test() {
      InputStream stream = getClass().getClassLoader().getResourceAsStream(exampleFile);
      if (stream == null) {
         fail("Cannot load file " + exampleFile);
      }
      try {
         Benchmark benchmark = BenchmarkParser.instance().buildBenchmark(stream, new LocalBenchmarkData());
         assertThat(benchmark.name()).isEqualTo(exampleFile.replace(".hf.yaml", "").replaceFirst("[^" + File.separatorChar + "]*.", ""));
         byte[] bytes = Util.serialize(benchmark);
         assertThat(bytes).isNotNull();
      } catch (Exception e) {
         throw new AssertionError("Failure in " + exampleFile, e);
      }
   }
}
