package io.hyperfoil.hotrod.resource;

import java.util.concurrent.CompletableFuture;

import io.hyperfoil.api.session.Session;

public class HotRodResource implements Session.Resource {

   private long startTimestampNanos;
   private long startTimestampMillis;
   private CompletableFuture future;

   public void set(CompletableFuture future, long startTimestampNanos, long startTimestampMillis) {
      this.future = future;
      this.startTimestampNanos = startTimestampNanos;
      this.startTimestampMillis = startTimestampMillis;
   }

   public boolean isComplete() {
      return this.future.isDone();
   }

   public long getStartTimestampMillis() {
      return startTimestampMillis;
   }

   public long getStartTimestampNanos() {
      return startTimestampNanos;
   }

   public long getSendTimestampNanos() {
      // TODO LATER send time is the difference between startTimestamp in NS and before the request goes to the wire
      return 0;
   }

   public static class Key implements Session.ResourceKey<HotRodResource> {

   }
}
