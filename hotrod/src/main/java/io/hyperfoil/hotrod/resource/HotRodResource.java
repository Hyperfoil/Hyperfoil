package io.hyperfoil.hotrod.resource;

import java.util.concurrent.CompletableFuture;

import io.hyperfoil.api.session.Session;

public class HotRodResource implements Session.Resource {

   private long startTimestampNanos;
   private long startTimestampMillis;
   private long firedTimestampMillis;
   private CompletableFuture future;

   public void set(CompletableFuture future, long startTimestampNanos, long startTimestampMillis, long firedTimestampMillis) {
      this.future = future;
      this.startTimestampNanos = startTimestampNanos;
      this.startTimestampMillis = startTimestampMillis;
      this.firedTimestampMillis = firedTimestampMillis;
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

   public long getFiredTimestampMillis() {
      return firedTimestampMillis;
   }

   public static class Key implements Session.ResourceKey<HotRodResource> {

   }
}
