package io.hyperfoil.core.impl.statistics;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.api.config.BenchmarkData;
import io.hyperfoil.api.config.ErgonomicsBuilder;
import io.hyperfoil.api.config.Phase;
import io.hyperfoil.api.http.HttpMethod;
import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.hyperfoil.core.steps.HttpRequestStep;
import io.vertx.ext.unit.junit.VertxUnitRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static io.hyperfoil.core.builders.StepCatalog.SC;
import static org.junit.Assert.*;

@RunWith(VertxUnitRunner.class)
public class StatisticsStoreTest {


   private StatisticsSnapshot makeSnapshot(int sequenceId, long start, long end, long... values) {
      StatisticsSnapshot snapshot = new StatisticsSnapshot();
      snapshot.sequenceId = sequenceId;
      snapshot.requestCount = values == null ? 0 : values.length;
      snapshot.histogram.setStartTimeStamp(start);
      snapshot.histogram.setEndTimeStamp(end);
      for (long value : values) {
         snapshot.histogram.recordValue(value);
      }
      return snapshot;
   }

   @Test
   public void allJson_empty_issue45() {
      ErgonomicsBuilder ergonomicsBuilder = new ErgonomicsBuilder();
      ergonomicsBuilder.repeatCookies(true).userAgentFromSession(true);
      Benchmark benchmark = new BenchmarkBuilder("originalSource", BenchmarkData.EMPTY)
            .name("benchmarkName")
            .http().host("localhost").endHttp()
            .build();
      StatisticsStore store = new StatisticsStore(benchmark, failure -> {
      });


      ByteArrayOutputStream baos = new ByteArrayOutputStream(10_000);
      JsonFactory jsonFactory = new JsonFactory();
      jsonFactory.setCodec(new ObjectMapper());

      try {
         JsonGenerator jsonGenerator = jsonFactory.createGenerator(baos);
         store.writeJson(jsonGenerator);
         jsonGenerator.close();
         String out = new String(baos.toByteArray());

      } catch (Exception e) {
         fail("Should not throw an Exception writing an empty StatisticsStore");
         e.printStackTrace();
      }
   }

   @Test
   public void allJson() {

      ErgonomicsBuilder ergonomicsBuilder = new ErgonomicsBuilder();
      ergonomicsBuilder.repeatCookies(true).userAgentFromSession(true);
      // @formatter:off
      Benchmark benchmark = new BenchmarkBuilder("originalSource", BenchmarkData.EMPTY)
            .name("benchmarkName")
            .http().host("localhost").endHttp()

            .addPhase("ramp/001/one").always(1)
               .duration(60_000)
               .scenario().initialSequence("one")
                  .step(SC).httpRequest(HttpMethod.GET)
                     .sla().addItem().meanResponseTime(1, TimeUnit.MILLISECONDS).endSLA().endList()
                  .endStep()
               .endSequence().endScenario()
            .endPhase()

            .addPhase("ramp/001/two").always(1)
               .duration(60_000)
               .scenario().initialSequence("two")
                  .step(SC).httpRequest(HttpMethod.GET)
                     .sla().addItem().meanResponseTime(1, TimeUnit.MILLISECONDS).endSLA().endList()
                  .endStep()
               .endSequence().endScenario()
            .endPhase()

            .addPhase("ramp/002/one").always(1)
               .duration(60_000)
               .scenario().initialSequence("test")
                  .step(SC).httpRequest(HttpMethod.GET)
                     .sla().addItem().meanResponseTime(1, TimeUnit.MILLISECONDS).endSLA().endList()
                  .endStep()
               .endSequence().endScenario()
            .endPhase()

            .addPhase("steady/002/one").always(1)
               .duration(60_000)
               .scenario().initialSequence("test")
                  .step(SC).httpRequest(HttpMethod.GET)
                     .sla().addItem().meanResponseTime(1, TimeUnit.MILLISECONDS).endSLA().endList()
                  .endStep()
               .endSequence().endScenario()
            .endPhase()

            .build();
      // @formatter:on
      Iterator<Phase> phaseIterator = benchmark.phases().iterator();
      Phase phase1 = phaseIterator.next();
      Phase phase2 = phaseIterator.next();
      Phase phase3 = phaseIterator.next();
      Phase phase4 = phaseIterator.next();

      HttpRequestStep step1 = (HttpRequestStep) Stream.of(phase1.scenario().sequences())
            .flatMap(s -> Stream.of(s.steps()))
            .filter(HttpRequestStep.class::isInstance)
            .findAny().get();
      HttpRequestStep step2 = (HttpRequestStep) Stream.of(phase2.scenario().sequences())
            .flatMap(s -> Stream.of(s.steps()))
            .filter(HttpRequestStep.class::isInstance)
            .findAny().get();
      HttpRequestStep step3 = (HttpRequestStep) Stream.of(phase3.scenario().sequences())
            .flatMap(s -> Stream.of(s.steps()))
            .filter(HttpRequestStep.class::isInstance)
            .findAny().get();
      HttpRequestStep step4 = (HttpRequestStep) Stream.of(phase4.scenario().sequences())
            .flatMap(s -> Stream.of(s.steps()))
            .filter(HttpRequestStep.class::isInstance)
            .findAny().get();

      StatisticsStore store = new StatisticsStore(benchmark, failure -> {
      });


      store.record("address", phase1.id(), step1.id(), "metric1",
            makeSnapshot(101, 0 * 60_000, 1 * 60_000, 1_000, 10_000, 30_000, 60_000, 120_000, 666_000_000)
      );
      store.recordSessionStats("address", 0 * 60_000, "ramp/001/one", 1, 10);
      store.completePhase("ramp/001/one");

      store.record("address", phase2.id(), step2.id(), "metric1",
            makeSnapshot(101, 0 * 60_000, 1 * 60_000, 1_000, 10_000, 30_000, 60_000, 120_000, 666_000_000)
      );
      store.recordSessionStats("address", 0 * 60_000, "ramp/001/two", 1, 10);
      store.completePhase("ramp/001/two");

      store.record("address", phase3.id(), step3.id(), "metric1",
            makeSnapshot(201, 1 * 60_000, 2 * 60_000, 1_000, 10_000, 30_000, 60_000, 120_000, 666_000_000)
      );
      store.recordSessionStats("address", 1 * 60_000, "ramp/002/one", 10, 20);
      store.completePhase("ramp/002/one");

      store.record("address", phase4.id(), step4.id(), "metric1",
            makeSnapshot(301, 2 * 60_000, 3 * 60_000, 1_000, 10_000, 30_000, 60_000, 120_000, 666_000_000)
      );
      store.recordSessionStats("address", 0 * 60_000, "steady/002/one", 20, 30);
      store.completePhase("steady/002/one");


      assertFalse(store.validateSlas());

      ByteArrayOutputStream baos = new ByteArrayOutputStream(10_000);
      JsonFactory jsonFactory = new JsonFactory();
      jsonFactory.setCodec(new ObjectMapper());

      try {
         JsonGenerator jsonGenerator = jsonFactory.createGenerator(baos);
         store.writeJson(jsonGenerator);
         jsonGenerator.close();
         String out = new String(baos.toByteArray());

         ObjectMapper mapper = new ObjectMapper();

         JsonNode node = mapper.readTree(out);


         assertTrue("expect an object", node.isObject());

         assertSame("root keys", Arrays.asList("total", "failure", "phase", "agent"), getKeys(node));
         assertSame("phase names", Arrays.asList("ramp", "steady"), getKeys(node.get("phase")));
         assertSame("ramp iterations", Arrays.asList("001", "002"), getKeys(node.get("phase").get("ramp").get("iteration")));
         assertSame("ramp/001 forks", Arrays.asList("one", "two"), getKeys(node.get("phase").get("ramp").get("iteration").get("001").get("fork")));


      } catch (IOException e) {
         e.printStackTrace();
      }
   }

   private List<String> getKeys(JsonNode node) {
      List<String> rtrn = new ArrayList<>();
      if (node != null) {
         node.fieldNames().forEachRemaining(rtrn::add);
      }
      return rtrn;
   }

   private void assertSame(String message, List<String> expected, List<String> actual) {
      if (expected.containsAll(actual) && actual.containsAll(expected)) {

      } else {
         fail(message + " expected " + expected + " but found " + actual);
      }
   }
}
