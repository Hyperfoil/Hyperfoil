package io.hyperfoil.cli.commands;

import static io.hyperfoil.http.steps.HttpStepCatalog.SC;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.api.config.PhaseBuilder;
import io.hyperfoil.core.handlers.TransferSizeRecorder;
import io.hyperfoil.http.api.HttpMethod;
import io.hyperfoil.http.config.HttpPluginBuilder;
import io.hyperfoil.http.config.Protocol;
import io.hyperfoil.impl.Util;

public abstract class WrkScenario {

   public enum PhaseType {
      calibration,
      test
   }

   public BenchmarkBuilder getBenchmarkBuilder(String name, String url, boolean enableHttp2, int connections,
         boolean useHttpCache, int threads, Map<String, String> agentParam,
         String calibrationDuration, String testDuration, String[][] parsedHeaders, String timeout)
         throws URISyntaxException {

      if (!url.startsWith("http://") && !url.startsWith("https://")) {
         url = "http://" + url;
      }

      URI uri = new URI(url);

      Protocol protocol = Protocol.fromScheme(uri.getScheme());
      BenchmarkBuilder builder = BenchmarkBuilder.builder()
            .name(name)
            .addPlugin(HttpPluginBuilder::new)
            .ergonomics()
            .repeatCookies(false)
            .userAgentFromSession(false)
            .endErgonomics()
            .http()
            .protocol(protocol).host(uri.getHost()).port(protocol.portOrDefault(uri.getPort()))
            .allowHttp2(enableHttp2)
            .sharedConnections(connections)
            .useHttpCache(useHttpCache)
            .endHttp()
            .endPlugin()
            .threads(threads);
      // @formatter:on
      if (agentParam != null) {
         for (Map.Entry<String, String> agent : agentParam.entrySet()) {
            Map<String, String> properties = Stream.of(agent.getValue().split(","))
                  .map(property -> {
                     String[] pair = property.split("=", 2);
                     if (pair.length != 2) {
                        throw new IllegalArgumentException("Cannot parse " + property
                              + " as a property: Agent should be formatted as -AagentName=key1=value1,key2=value2...");
                     }
                     return pair;
                  })
                  .collect(Collectors.toMap(keyValue -> keyValue[0], keyValue -> keyValue[1]));
            builder.addAgent(agent.getKey(), null, properties);
         }
      }

      String path = getPath(uri);

      addPhase(builder, PhaseType.calibration, calibrationDuration, parsedHeaders, timeout, path);
      // We can start only after calibration has full completed because otherwise some sessions
      // would not have connection available from the beginning.
      addPhase(builder, PhaseType.test, testDuration, parsedHeaders, timeout, path)
            .startAfterStrict(PhaseType.calibration.name())
            .maxDuration(Util.parseToMillis(testDuration));
      return builder;
   }

   protected abstract PhaseBuilder<?> phaseConfig(PhaseBuilder.Catalog catalog, PhaseType phaseType, long durationMs);

   private PhaseBuilder<?> addPhase(BenchmarkBuilder benchmarkBuilder, PhaseType phaseType, String durationStr,
         String[][] parsedHeaders, String timeout, String path) {
      long duration = Util.parseToMillis(durationStr);
      // @formatter:off
      var scenarioBuilder = phaseConfig(benchmarkBuilder.addPhase(phaseType.name()), phaseType, duration)
            .duration(duration)
            .maxDuration(duration + Util.parseToMillis(timeout))
            .scenario();
      // even with pipelining or HTTP 2 multiplexing
      // each session lifecycle requires to fully complete (with response)
      // before being reused, hence the number of requests which can use is just 1
      scenarioBuilder.maxRequests(1);
      // same reasoning here: given that the default concurrency of sequence is 0 for initialSequences
      // and there's a single sequence too, there's no point to have more than 1 per session
      scenarioBuilder.maxSequences(1);
      return scenarioBuilder
            .initialSequence("request")
            .step(SC).httpRequest(HttpMethod.GET)
            .path(path)
            .headerAppender((session, request) -> {
               if (parsedHeaders != null) {
                  for (String[] header : parsedHeaders) {
                     request.putHeader(header[0], header[1]);
                  }
               }
            })
            .timeout(timeout)
            .handler()
            .rawBytes(new TransferSizeRecorder("transfer"))
            .endHandler()
            .endStep()
            .endSequence()
            .endScenario();
      // @formatter:on
   }

   private String getPath(URI uri) {
      String path = uri.getPath();
      if (path == null || path.isEmpty()) {
         path = "/";
      }
      if (uri.getQuery() != null) {
         path = path + "?" + uri.getQuery();
      }
      if (uri.getFragment() != null) {
         path = path + "#" + uri.getFragment();
      }
      return path;
   }
}
