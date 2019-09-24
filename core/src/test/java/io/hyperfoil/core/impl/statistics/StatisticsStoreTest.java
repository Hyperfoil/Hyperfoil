package io.hyperfoil.core.impl.statistics;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.api.config.BenchmarkData;
import io.hyperfoil.api.config.ErgonomicsBuilder;
import io.hyperfoil.api.config.Phase;
import io.hyperfoil.api.http.HttpMethod;
import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.hyperfoil.core.builders.StepCatalog;
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
      Benchmark benchmark = new BenchmarkBuilder("originalSource", BenchmarkData.EMPTY)
         .name("benchmarkName")
         .http().host("localhost").endHttp()
         .addPhase("phaseName/001/test").always(1)
         .duration(60_000)
         .scenario().initialSequence("test")
         .step(StepCatalog.class).httpRequest(HttpMethod.GET)
         .sla().addItem().meanResponseTime(1, TimeUnit.MILLISECONDS).endSLA().endList()
         .endStep()
         .endSequence().endScenario()
         .endPhase()
         .build();
      Phase phase = benchmark.phases().stream().findAny().get();
      HttpRequestStep step = (HttpRequestStep) Stream.of(phase.scenario().sequences())
         .flatMap(s -> Stream.of(s.steps()))
         .filter(HttpRequestStep.class::isInstance)
         .findAny().get();

      StatisticsStore store = new StatisticsStore(benchmark, failure -> {
      });

      store.record("address", phase.id(), step.id(), "metric1",
         makeSnapshot(101, 0 * 60_000, 1 * 60_000, 1_000, 10_000, 30_000, 60_000, 120_000, 666_000_000)
      );
      store.recordSessionStats("address", 0 * 60_000, "phaseName/001/test", 1, 10);
      store.record("address", phase.id(), step.id(), "metric1",
         makeSnapshot(201, 1 * 60_000, 2 * 60_000, 1_000, 10_000, 30_000, 60_000, 120_000, 666_000_000)
      );
      store.recordSessionStats("address", 1 * 60_000, "phaseName/001/test", 10, 20);

      store.completePhase("phaseName/001/test");

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

         ObjectNode objectNode = (ObjectNode) node;
         Iterator<String> fieldNameIterator = objectNode.fieldNames();
         List<String> fieldNames = new ArrayList<>();
         fieldNameIterator.forEachRemaining(fieldNames::add);

         assertTrue("missing a key in " + fieldNames, fieldNames.containsAll(Arrays.asList("total", "failure", "phase", "agent")));
         assertEquals("expect 4 keys", 4, fieldNames.size());

      } catch (IOException e) {
         e.printStackTrace();
      }
   }
}
