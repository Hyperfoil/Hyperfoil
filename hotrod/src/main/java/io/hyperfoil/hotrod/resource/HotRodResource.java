package io.hyperfoil.hotrod.resource;

import java.util.concurrent.CompletableFuture;

import io.hyperfoil.api.session.Session;

public class HotRodResource implements Session.Resource {

   public final long[] timestamps = new long[2];
   private CompletableFuture future;

   public void set(CompletableFuture future) {
      this.future = future;
   }

   public boolean isComplete() {
      return this.future.isDone();
   }

   public long getStartTimestampMillis() {
      return timestamps[0];
   }

   public long getStartTimestampNanos() {
      return timestamps[1];
   }

   public static class Key implements Session.ResourceKey<HotRodResource> {

   }
}
