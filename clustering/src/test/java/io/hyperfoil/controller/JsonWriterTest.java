package io.hyperfoil.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.StringWriter;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.SLA;
import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.vertx.core.json.JsonObject;

public class JsonWriterTest {

   @Test
   public void shouldComputePercentileResponseTimeForFailure() throws Exception {
      var benchmark = Benchmark.forTesting();
      StatisticsStore store = new StatisticsStore(benchmark, f -> {
      }, false);
      StatisticsSnapshot snapshot = new StatisticsSnapshot();
      var samples = new long[] { 100, 200, 300, 400, 500, 600, 700, 800, 900, 1000 };
      var histo = snapshot.histogram;
      for (long sample : samples) {
         histo.recordValue(sample);
      }
      // create a copy of the histogram
      var latencies = snapshot.histogram.copy();
      // inject a failure using that snapshot
      SLA.Failure failure = new SLA.Failure(null, "phase1", "metric1", snapshot, "cause");
      store.addFailure(failure);

      // capture JSON output
      StringWriter writer = new StringWriter();
      JsonFactory jfactory = new JsonFactory();
      jfactory.setCodec(new ObjectMapper());
      try (JsonGenerator jGenerator = jfactory.createGenerator(writer)) {
         JsonWriter.writeArrayJsons(store, jGenerator, new JsonObject());
      }

      // parse and verify percentiles
      ObjectMapper mapper = new ObjectMapper();
      JsonNode root = mapper.readTree(writer.toString());
      JsonNode p = root.path("failures").get(0).path("percentileResponseTime");
      String[] expectedPercentiles = { "50.0", "90.0", "99.0", "99.9", "99.99" };
      assertEquals(expectedPercentiles.length, p.size());
      var names = p.fieldNames();
      for (int i = 0; i < expectedPercentiles.length; i++) {
         assertEquals(expectedPercentiles[i], names.next());
      }
      double[] expectedPercentileValues = new double[] {
            latencies.getValueAtPercentile(50.0),
            latencies.getValueAtPercentile(90.0),
            latencies.getValueAtPercentile(99.0),
            latencies.getValueAtPercentile(99.9),
            latencies.getValueAtPercentile(99.99)
      };
      for (int i = 0; i < expectedPercentileValues.length; i++) {
         String percentileName = expectedPercentiles[i];
         double expectedValue = expectedPercentileValues[i];
         assertEquals(expectedValue, p.get(percentileName).asDouble(), "Percentile " + percentileName);
      }
   }
}
