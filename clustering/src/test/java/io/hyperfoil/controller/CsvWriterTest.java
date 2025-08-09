package io.hyperfoil.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.hyperfoil.api.config.Benchmark;

public class CsvWriterTest {

   @Test
   public void testWriteCsvProducesCorrectHeader(@TempDir Path dir) throws IOException {
      StatisticsStore store = new StatisticsStore(Benchmark.forTesting(), f -> {
      });
      CsvWriter.writeCsv(dir, store);

      List<String> lines = Files.readAllLines(dir.resolve("total.csv"));
      assertFalse(lines.isEmpty());
      String expected = "Phase,Metric,Start,End," +
            "Requests,Responses,Mean,StdDev,Min," +
            "p50.0,p90.0,p99.0,p99.9,p99.99,Max" +
            ",ConnectionErrors,RequestTimeouts,InternalErrors,Invalid,BlockedTime" +
            ",MinSessions,MaxSessions";
      assertEquals(expected, lines.get(0));
   }
}
