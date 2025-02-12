package io.hyperfoil.example;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.api.config.Model;
import io.hyperfoil.api.config.Phase;
import io.hyperfoil.api.config.PhaseBuilder;
import io.hyperfoil.api.config.PhaseForkBuilder;
import io.hyperfoil.api.config.Scenario;
import io.hyperfoil.core.impl.LocalBenchmarkData;
import io.hyperfoil.core.parser.BenchmarkParser;
import io.hyperfoil.core.parser.ParserException;
import io.hyperfoil.core.print.YamlVisitor;
import io.hyperfoil.function.SerializableSupplier;
import io.hyperfoil.http.config.HttpPluginBuilder;
import io.hyperfoil.impl.Util;

public class ValidateExampleTest {

   // parameters source
   public static Stream<Arguments> exampleFiles() throws IOException {
      return Files.list(Paths.get("examples"))
            .filter(p -> p.toString().endsWith(".hf.yaml"))
            .filter(p -> p.toFile().isFile())
            .map(p -> Arguments.of(p.toString()));
   }

   private InputStream loadOrFail(String exampleFile) {
      InputStream stream = getClass().getClassLoader().getResourceAsStream(exampleFile);
      if (stream == null) {
         fail("Cannot load file " + exampleFile);
      }
      return stream;
   }

   @ParameterizedTest
   @MethodSource("exampleFiles")
   public void testSerializable(String exampleFile) {
      try {
         Benchmark benchmark = BenchmarkParser.instance().buildBenchmark(loadOrFail(exampleFile),
               new LocalBenchmarkData(Paths.get(exampleFile)), Collections.emptyMap());
         assertThat(benchmark.name())
               .isEqualTo(exampleFile.replace(".hf.yaml", "").replaceFirst("[^" + File.separatorChar + "]*.", ""));
         byte[] bytes = Util.serialize(benchmark);
         assertThat(bytes).isNotNull();
      } catch (Exception e) {
         throw new AssertionError("Failure in " + exampleFile, e);
      }
   }

   @ParameterizedTest
   @MethodSource("exampleFiles")
   public void testCopy(String exampleFile) {
      try {
         LocalBenchmarkData data = new LocalBenchmarkData(Paths.get(exampleFile));
         BenchmarkBuilder original = BenchmarkParser.instance().builder(loadOrFail(exampleFile), data, Collections.emptyMap());
         BenchmarkBuilder builder = new BenchmarkBuilder(null, Collections.emptyMap()).data(data);
         HttpPluginBuilder plugin = builder.addPlugin(HttpPluginBuilder::new);
         HttpPluginBuilder.httpForTesting(original).forEach(http -> plugin.addHttp(http.copy(plugin)));
         builder.prepareBuild();
         for (PhaseBuilder<?> phase : BenchmarkBuilder.phasesForTesting(original)) {
            TestingPhaseBuilder copy = new TestingPhaseBuilder(builder);
            // This triggers the copy
            copy.readForksFrom(phase);
            copy.prepareBuild();
            copy.build(null, new AtomicInteger());
         }
      } catch (Exception e) {
         throw new AssertionError("Failure in " + exampleFile, e);
      }
   }

   @ParameterizedTest
   @MethodSource("exampleFiles")
   public void testPrint(String exampleFile) throws IOException, ParserException {
      Benchmark benchmark = BenchmarkParser.instance().buildBenchmark(loadOrFail(exampleFile),
            new LocalBenchmarkData(Paths.get(exampleFile)), Collections.emptyMap());
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      try (PrintStream stream = new PrintStream(output, false, StandardCharsets.UTF_8)) {
         new YamlVisitor(stream, 20).walk(benchmark);
      }
      String str = output.toString(StandardCharsets.UTF_8);
      // We want the common stuff properly named
      assertThat(str).doesNotContain("<recursion detected>");
      assertThat(str).doesNotContain("<lambda>");
   }

   private static class TestingPhaseBuilder extends PhaseBuilder<TestingPhaseBuilder> {
      protected TestingPhaseBuilder(BenchmarkBuilder builder) {
         super(builder, "-for-testing-" + ThreadLocalRandom.current().nextLong());
      }

      @Override
      protected Phase buildPhase(SerializableSupplier<Benchmark> benchmark, int phaseId, int iteration, PhaseForkBuilder f) {
         Scenario scenario = f.scenario().build();
         assert scenario != null;
         return PhaseBuilder.noop(null, 0, 0, name, 0, null, null, null);
      }

      @Override
      protected Model createModel(int iteration, double weight) {
         throw new UnsupportedOperationException();
      }
   }
}
