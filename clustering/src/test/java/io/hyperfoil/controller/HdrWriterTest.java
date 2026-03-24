package io.hyperfoil.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import io.hyperfoil.api.config.Benchmark;

public class HdrWriterTest {

   @Test
   public void testPersistHdrs(@TempDir(cleanup = CleanupMode.ALWAYS) Path dir) throws IOException {
      StatisticsStore store = new StatisticsStore(Benchmark.forTesting(), f -> {
      }, true);
      HdrWriter.writeHdr(dir, store);

      List<String> lines = Files.readAllLines(dir.resolve("total.hgrm"));
      assertFalse(lines.isEmpty());
      String expected = "#[Histogram log format version 1.3]";
      assertEquals(expected, lines.getFirst());
   }
}
