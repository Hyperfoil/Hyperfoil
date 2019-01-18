package io.hyperfoil.core.steps;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import io.hyperfoil.api.config.Sequence;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.api.ResourceUtilizer;
import io.hyperfoil.core.builders.BaseSequenceBuilder;
import io.hyperfoil.core.builders.BaseStepBuilder;
import io.hyperfoil.core.util.Util;
import io.hyperfoil.function.SerializableSupplier;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class ScheduleDelayStep implements Step, ResourceUtilizer {
   private static final Logger log = LoggerFactory.getLogger(ScheduleDelayStep.class);

   private final Object key;
   private final Type type;
   private final long duration, min, max;
   private final boolean negativeExponential;

   public ScheduleDelayStep(Object key, Type type, long duration, boolean negativeExponential, long min, long max) {
      this.key = key;
      this.type = type;
      this.duration = duration;
      this.negativeExponential = negativeExponential;
      this.min = min;
      this.max = max;
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
      long relativeDelay = duration;
      if (negativeExponential) {
         double rand = ThreadLocalRandom.current().nextDouble();
         relativeDelay = (long) ((duration) * -Math.log(Math.max(rand, 1e-20d)));
         if (relativeDelay < min) {
            relativeDelay = min;
         } else if (relativeDelay > max) {
            relativeDelay = max;
         }
      }
      blockedUntil.timestamp = baseTimestamp + relativeDelay;
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
      private Type type = Type.FROM_NOW;
      private boolean negativeExponential;
      private long min;
      private long max;

      public Builder(BaseSequenceBuilder parent, Object key, long duration, TimeUnit timeUnit) {
         super(parent);
         this.key = key;
         this.duration = timeUnit == null ? 0 : timeUnit.toMillis(duration);
      }

      public Builder key(String key) {
         this.key = key;
         return this;
      }

      public Builder duration(String duration) {
         this.duration = Util.parseToMillis(duration);
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

      public Builder negativeExponential() {
         this.negativeExponential = true;
         return this;
      }

      public Builder min(String min) {
         this.min = Util.parseToMillis(min);
         return this;
      }

      public Builder max(String max) {
         this.max = Util.parseToMillis(max);
         return this;
      }

      @Override
      public List<Step> build(SerializableSupplier<Sequence> sequence) {
         return Collections.singletonList(new ScheduleDelayStep(key, type, duration, negativeExponential, min, max));
      }

      public Builder type(Type type) {
         this.type = type;
         return this;
      }
   }
}
