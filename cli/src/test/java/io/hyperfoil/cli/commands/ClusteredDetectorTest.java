package io.hyperfoil.cli.commands;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.hyperfoil.core.impl.ProvidedBenchmarkData;

public class ClusteredDetectorTest {

   private static final String HEADER = "name: test\n"
         + "http:\n"
         + "  host: http://localhost:8080\n"
         + "phases:\n"
         + "- test:\n"
         + "    always:\n"
         + "      users: 1\n"
         + "      duration: 1s\n"
         + "      scenario:\n"
         + "      - step:\n"
         + "        - httpRequest:\n"
         + "            GET: /\n";

   private static boolean hasAgents(String yaml, Map<String, String> params) {
      return ClusteredDetector.hasAgents(yaml, new ProvidedBenchmarkData(), params);
   }

   @Test
   public void noAgents() {
      assertFalse(hasAgents(HEADER, Map.of()));
   }

   @Test
   public void agentsMapping() {
      assertTrue(hasAgents(HEADER + "agents:\n  a1: host1\n  a2: host2\n", Map.of()));
   }

   @Test
   public void agentsInline() {
      assertTrue(hasAgents(HEADER + "agents: host1\n", Map.of()));
   }

   @Test
   public void emptyAgentsSection() {
      assertFalse(hasAgents(HEADER + "agents:\n", Map.of()));
   }

   @Test
   public void agentsFromParam() {
      String yaml = HEADER + "agents:\n  a1: !param HOST\n";
      assertTrue(hasAgents(yaml, Map.of("HOST", "host1")));
   }

   /**
    * A template whose mandatory parameters are not on the command line cannot be built here - they are read
    * interactively later on. The textual fallback has to decide.
    */
   @Test
   public void missingMandatoryParamFallsBackToTextualCheck() {
      String template = HEADER.replace("host: http://localhost:8080", "host: !param HOST");
      assertTrue(hasAgents(template + "agents:\n  a1: host1\n", Map.of()));
      assertFalse(hasAgents(template, Map.of()));
   }

   /**
    * Benchmarks loaded from a URL start with no extra files, so any benchmark referencing one fails to build.
    */
   @Test
   public void missingFileFallsBackToTextualCheck() {
      String yaml = "name: test\nhttp:\n  host: http://localhost:8080\n"
            + "agents:\n  a1: host1\n"
            + "phases:\n- test:\n    always:\n      users: 1\n      duration: 1s\n      scenario:\n"
            + "      - step:\n        - randomItem:\n            fromFile: nosuch.csv\n            toVar: x\n";
      assertTrue(hasAgents(yaml, Map.of()));
      assertFalse(hasAgents(yaml.replace("agents:\n  a1: host1\n", ""), Map.of()));
   }

   @Test
   public void textualCheckIgnoresNestedAndCommentedAgents() {
      assertFalse(ClusteredDetector.hasAgentsSection("foo:\n  agents:\n    a1: host1\n"));
      assertFalse(ClusteredDetector.hasAgentsSection("# agents:\n#   a1: host1\n"));
      assertTrue(ClusteredDetector.hasAgentsSection("agents:\n- host1\n"));
   }

}
