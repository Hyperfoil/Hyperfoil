package io.hyperfoil.clustering.webcli;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import io.hyperfoil.api.config.BenchmarkData;
import io.hyperfoil.cli.context.HyperfoilCommandInvocation;

class WebBenchmarkData implements BenchmarkData {
   Map<String, byte[]> files = new HashMap<>();

   @Override
   public InputStream readFile(String file) {
      byte[] bytes = files.get(file);
      if (bytes != null) {
         return new ByteArrayInputStream(bytes);
      }
      throw new MissingFileException(file);
   }

   @Override
   public Map<String, byte[]> files() {
      return files;
   }

   void loadFile(HyperfoilCommandInvocation invocation, WebCliContext context, String file) throws InterruptedException {
      CountDownLatch latch;
      synchronized (context) {
         latch = context.latch = new CountDownLatch(1);
      }
      invocation.println("__HYPERFOIL_LOAD_FILE__" + file);
      latch.await();
      synchronized (context) {
         context.latch = null;
         if (context.binaryContent == null) {
            throw new InterruptedException();
         }
         files.put(file, context.binaryContent.toByteArray());
         context.binaryContent = null;
         invocation.println("File " + file + " uploaded.");
      }
   }
}
