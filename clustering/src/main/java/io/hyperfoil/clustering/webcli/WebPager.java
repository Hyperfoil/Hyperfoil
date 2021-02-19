package io.hyperfoil.clustering.webcli;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;

import io.hyperfoil.cli.Pager;
import io.hyperfoil.cli.context.HyperfoilCommandInvocation;

class WebPager implements Pager {
   @Override
   public void open(HyperfoilCommandInvocation invocation, String text, String prefix, String suffix) {
      invocation.println("__HYPERFOIL_PAGER_MAGIC__");
      invocation.println(text);
      try {
         WebCliContext context = (WebCliContext) invocation.context();
         CountDownLatch latch;
         synchronized (context) {
            latch = context.latch;
         }
         latch.await();
         synchronized (context) {
            context.latch = null;
         }
      } catch (InterruptedException e) {
         // interruption is okay
      }
   }

   @Override
   public void open(HyperfoilCommandInvocation invocation, File file) {
      try {
         open(invocation, Files.readString(file.toPath(), StandardCharsets.UTF_8), null, null);
      } catch (IOException e) {
         invocation.error("Cannot open file " + file.getName());
      }
   }
}
