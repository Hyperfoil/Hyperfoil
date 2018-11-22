package io.sailrocket.core.steps;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.sailrocket.api.config.Step;
import io.sailrocket.api.session.Session;
import io.sailrocket.core.api.ResourceUtilizer;
import io.sailrocket.core.builders.BaseSequenceBuilder;
import io.sailrocket.core.builders.BaseStepBuilder;
import io.sailrocket.core.util.Util;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class ScheduleDelayStep implements Step, ResourceUtilizer {
   private static final Logger log = LoggerFactory.getLogger(ScheduleDelayStep.class);

   private final Object key;
   private final Type type;
   private final long duration;
   private final TimeUnit timeUnit;

   public ScheduleDelayStep(Object key, Type type, long duration, TimeUnit timeUnit) {
      this.key = key;
      this.type = type;
      this.duration = duration;
      this.timeUnit = timeUnit;
   }

   @Override
   public boolean invoke(Session session) {
      Timestamp blockedUntil = (Timestamp) session.activate(key);
      long now = System.currentTimeMillis();
      long baseTimestamp;
      switch (type) {
         case FROM_LAST:
            if (blockedUntil.timestamp != Long.MAX_VALUE) {
               baseTimestamp = blockedUntil.timestamp;
               break;
            }
            // bo break;
         case FROM_NOW:
            baseTimestamp = now;
            break;
         default:
            throw new IllegalStateException();
      }
      blockedUntil.timestamp = baseTimestamp + timeUnit.toMillis(duration);
      long delay = blockedUntil.timestamp - now;
      if (delay > 0) {
         log.trace("Scheduling #{} to run in {}", session.uniqueId(), delay);
         session.executor().schedule((Runnable) session, delay, TimeUnit.MILLISECONDS);
      }
      return true;
   }

   @Override
   public void reserve(Session session) {
      session.declare(key);
      session.setObject(key, new Timestamp());
   }

   public enum Type {
      FROM_LAST,
      FROM_NOW,
   }

   static class Timestamp {
      long timestamp = Long.MAX_VALUE;
   }

   public static class Builder extends BaseStepBuilder {
      private Object key;
      private long duration;
      private TimeUnit timeUnit;
      private Type type = Type.FROM_NOW;

      public Builder(BaseSequenceBuilder parent, Object key, long duration, TimeUnit timeUnit) {
         super(parent);
         this.key = key;
         this.duration = duration;
         this.timeUnit = timeUnit;
      }

      public Builder key(String key) {
         this.key = key;
         return this;
      }

      public Builder duration(String duration) {
         this.duration = Util.parseToMillis(duration);
         this.timeUnit = TimeUnit.MILLISECONDS;
         return this;
      }

      public Builder fromNow() {
         type = Type.FROM_NOW;
         return this;
      }

      public Builder fromLast() {
         type = Type.FROM_LAST;
         return this;
      }

      @Override
      public List<Step> build() {
         return Collections.singletonList(new ScheduleDelayStep(key, type, duration, timeUnit));
      }

      public Builder type(Type type) {
         this.type = type;
         return this;
      }
   }
}
