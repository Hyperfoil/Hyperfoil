package io.hyperfoil.clustering;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class Zipper {
   private static final Logger log = LogManager.getLogger(Zipper.class);

   private final HttpServerResponse response;
   private final ZipOutputStream zipStream;
   private final Queue<File> files = new LinkedList<>();
   private final Path dir;

   public Zipper(HttpServerResponse response, Path dir) {
      this.response = response;
      this.zipStream = new ZipOutputStream(new OutputStreamAdapter(response));
      this.dir = dir;
      files.addAll(Arrays.asList(dir.toFile().listFiles()));
      response.putHeader(HttpHeaders.CONTENT_TYPE, "application/zip");
      response.setChunked(true);
      response.drainHandler(nil -> run());
   }

   public void run() {
      if (response.closed()) {
         return;
      }
      while (!response.writeQueueFull()) {
         File file = files.poll();
         if (file == null) {
            try {
               zipStream.close();
            } catch (IOException e) {
               log.error("Failed closing zip stream", e);
               return;
            } finally {
               response.end();
            }
         }
         if (file.isDirectory()) {
            files.addAll(Arrays.asList(file.listFiles()));
         } else {
            Path path = file.toPath();
            try {
               zipStream.putNextEntry(new ZipEntry(dir.relativize(path).toString()));
               zipStream.write(Files.readAllBytes(path));
               zipStream.closeEntry();
            } catch (IOException e) {
               log.error("Failed writing file {}", e, path);
               response.end();
               return;
            }
         }
      }
   }
}
