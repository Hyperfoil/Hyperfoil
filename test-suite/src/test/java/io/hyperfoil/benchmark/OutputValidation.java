package io.hyperfoil.benchmark;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.zip.ZipFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Shared assertions for the JSON/CSV output produced by the "run" and "wrk2" commands.
 */
public final class OutputValidation {

   private OutputValidation() {
   }

   public static void validateJsonContent(Path jsonFile) throws IOException {
      String content = Files.readString(jsonFile);
      ObjectMapper mapper = new ObjectMapper();
      JsonNode root = mapper.readTree(content);
      assertTrue(root.has("stats"), "Expected JSON to contain 'stats' key");
      assertTrue(root.get("stats").isArray(), "Expected 'stats' to be an array");
   }

   public static void validateCsvZipContent(Path zipFile, Path extractDir) throws IOException {
      byte[] content = Files.readAllBytes(zipFile);
      assertTrue(content.length > 0, "Expected zip file to have content");
      assertTrue(content[0] == 0x50 && content[1] == 0x4B, "Expected zip file format (PK signature)");

      Path unzipDir = extractDir.resolve("unzip_" + UUID.randomUUID());
      Files.createDirectories(unzipDir);

      try (ZipFile zf = new ZipFile(zipFile.toFile())) {
         zf.stream().forEach(entry -> {
            try {
               Path entryPath = unzipDir.resolve(entry.getName());
               if (entry.isDirectory()) {
                  Files.createDirectories(entryPath);
               } else {
                  Files.createDirectories(entryPath.getParent());
                  Files.copy(zf.getInputStream(entry), entryPath);
               }
            } catch (IOException e) {
               throw new RuntimeException(e);
            }
         });
      }

      var csvFiles = Files.list(unzipDir)
            .filter(p -> p.toString().endsWith(".csv"))
            .toList();
      assertTrue(!csvFiles.isEmpty(), "Expected at least one CSV file in the zip archive");

      for (Path csvFile : csvFiles) {
         String csvContent = Files.readString(csvFile);
         assertTrue(csvContent.length() > 0, "Expected CSV file to have content");
         assertTrue(csvContent.contains(","), "Expected CSV file to contain comma separators");
      }
   }
}
