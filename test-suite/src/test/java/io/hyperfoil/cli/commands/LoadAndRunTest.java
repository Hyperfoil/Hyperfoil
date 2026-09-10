package io.hyperfoil.cli.commands;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import io.hyperfoil.api.statistics.StatisticsSummary;
import io.hyperfoil.controller.model.Phase;
import io.hyperfoil.controller.model.RequestStatisticsResponse;
import io.hyperfoil.controller.model.RequestStats;
import io.hyperfoil.controller.model.Run;

public class LoadAndRunTest {

   @Test
   public void detectsRepresentativeFailures() {
      Run successfulRun = run(Collections.emptyList());
      RequestStatisticsResponse successfulStats = stats(summary(0));

      assertFalse(LoadAndRun.LoadAndRunCommand.hasErrors(successfulRun, successfulStats));
      assertTrue(LoadAndRun.LoadAndRunCommand.hasErrors(run(List.of("runtime error")), successfulStats));
      assertTrue(LoadAndRun.LoadAndRunCommand.hasErrors(successfulRun, stats(summary(1))));
   }

   private static Run run(List<String> errors) {
      Phase phase = new Phase("test", "TERMINATED", "atOnce", new Date(), null, new Date(), false, "0 ms", null);
      return new Run("0000", "benchmark", new Date(), new Date(), false, true, true, null, List.of(phase),
            Collections.emptyList(), errors);
   }

   private static RequestStatisticsResponse stats(StatisticsSummary summary) {
      return new RequestStatisticsResponse("TERMINATED",
            List.of(new RequestStats("test", 0, "request", summary, Collections.emptyList(), false)));
   }

   private static StatisticsSummary summary(int invalid) {
      return new StatisticsSummary(0, 1, 0, 0, 0, 0, new TreeMap<>(), 1, 1, invalid, 0, 0, 0, 0, new TreeMap<>());
   }
}
