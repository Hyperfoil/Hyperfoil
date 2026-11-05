package io.hyperfoil.cli.commands;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.BenchmarkData;
import io.hyperfoil.api.config.BenchmarkSource;
import io.hyperfoil.cli.CliUtil;
import io.hyperfoil.core.impl.LocalBenchmarkData;
import io.hyperfoil.core.impl.ProvidedBenchmarkData;
import io.hyperfoil.core.parser.BenchmarkParser;

/**
 * Decides whether the local controller should be started in clustered mode: that is the case when the
 * benchmark declares any agents.
 * <p>
 * Any failure (unreadable file, template with missing parameters, parse error...) results in
 * {@code false} - the subsequent upload reports the actual problem to the user.
 */
final class ClusteredDetector {
   private static final Duration DOWNLOAD_TIMEOUT = Duration.ofSeconds(15);
   private static final Pattern AGENTS_LINE = Pattern.compile("^agents\\s*:(.*)$");

   private ClusteredDetector() {
   }

   static boolean hasAgents(String benchmarkPath, Map<String, String> params) {
      if (benchmarkPath == null || benchmarkPath.isBlank()) {
         return false;
      }
      try {
         String yaml;
         BenchmarkData data;
         if (isUrl(benchmarkPath)) {
            yaml = download(benchmarkPath);
            data = new ProvidedBenchmarkData();
         } else {
            Path path = Paths.get(CliUtil.sanitize(benchmarkPath)).toAbsolutePath();
            yaml = Files.readString(path);
            data = new LocalBenchmarkData(path);
         }
         return hasAgents(yaml, data, params);
      } catch (IOException | InterruptedException | RuntimeException e) {
         return false;
      }
   }

   static boolean hasAgents(String yaml, BenchmarkData data, Map<String, String> params) {
      try {
         BenchmarkSource source = BenchmarkParser.instance().createSource(yaml, data);
         Benchmark benchmark = BenchmarkParser.instance().buildBenchmark(source, params);
         return benchmark.agents().length > 0;
      } catch (Throwable e) {
         // the benchmark cannot be built here (missing files, missing template parameters, ...);
         // fall back to a purely textual check of the top-level agents section
         return hasAgentsSection(yaml);
      }
   }

   /**
    * Textual fallback: is there a non-empty top-level {@code agents} section?
    */
   static boolean hasAgentsSection(String yaml) {
      String[] lines = yaml.split("\n", -1);
      for (int i = 0; i < lines.length; ++i) {
         Matcher m = AGENTS_LINE.matcher(stripComment(lines[i]));
         if (!m.matches()) {
            continue;
         }
         if (!m.group(1).isBlank()) {
            return true;
         }
         // the value may be on the following lines
         for (int j = i + 1; j < lines.length; ++j) {
            String next = stripComment(lines[j]);
            if (next.isBlank()) {
               continue;
            }
            return next.startsWith(" ") || next.startsWith("\t") || next.startsWith("-");
         }
         return false;
      }
      return false;
   }

   private static String stripComment(String line) {
      String trimmed = line.stripTrailing();
      return trimmed.stripLeading().startsWith("#") ? "" : trimmed;
   }

   private static boolean isUrl(String benchmark) {
      return benchmark.startsWith("http://") || benchmark.startsWith("https://");
   }

   private static String download(String url) throws IOException, InterruptedException {
      HttpClient client = HttpClient.newBuilder().connectTimeout(DOWNLOAD_TIMEOUT).build();
      HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(DOWNLOAD_TIMEOUT).GET().build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
         throw new IOException("Unexpected status " + response.statusCode());
      }
      return response.body();
   }

}
