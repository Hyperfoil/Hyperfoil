package io.hyperfoil.core.steps;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import io.hyperfoil.api.config.Sequence;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.config.BaseSequenceBuilder;
import io.hyperfoil.core.builders.BaseStepBuilder;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.function.SerializableSupplier;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class ScheduleDelayStep implements Step, ResourceUtilizer {
   private static final Logger log = LoggerFactory.getLogger(ScheduleDelayStep.class);

   private final Access key;
   private final Type type;
   private final long duration, min, max;
   private final boolean negativeExponential;

   public ScheduleDelayStep(Object key, Type type, long duration, boolean negativeExponential, long min, long max) {
      this.key = SessionFactory.access(key);
      this.type = type;
      this.duration = duration;
      this.negativeExponential = negativeExponential;
      this.min = min;
      this.max = max;
   }

   @Override
   public boolean invoke(Session session) {
      Timestamp blockedUntil = (Timestamp) key.activate(session);
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
         session.executor().schedule((Callable<?>) session, delay, TimeUnit.MILLISECONDS);
      }
      return true;
   }

   @Override
   public void reserve(Session session) {
      key.declareObject(session);
      key.setObject(session, new Timestamp());
   }

   public enum Type {
      FROM_LAST,
      FROM_NOW,
   }

   static class Timestamp {
      long timestamp = Long.MAX_VALUE;
   }

   /**
    * Define a point in future until which we should wait. Does not cause waiting.
    */
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

      /**
       * Key that is referenced later in `awaitDelay` step.
       * If you're introducing the delay through `thinkTime` do not use this property.
       *
       * @param key Identifier.
       * @return Self.
       */
      public Builder key(String key) {
         this.key = key;
         return this;
      }

      /**
       * Duration of the delay with appropriate suffix (e.g. `ms` or `s`).
       *
       * @param duration Delay duration.
       * @return Self.
       */
      public Builder duration(String duration) {
         this.duration = io.hyperfoil.util.Util.parseToMillis(duration);
         return this;
      }

      /**
       * Set this step invocation as the delay point reference; it will be computed as <code>now + duration</code>.
       *
       * @return Self.
       */
      public Builder fromNow() {
         type = Type.FROM_NOW;
         return this;
      }

      /**
       * Set previous delay point reference as the reference for next delay point; it will be computed as <code>(previous delay point or now) + duration</code>.
       *
       * @return Self.
       */
      public Builder fromLast() {
         type = Type.FROM_LAST;
         return this;
      }

      /**
       * Randomize the duration with negative-exponential distribution, using <code>duration</code> as the mean value.
       *
       * @return Self.
       */
      public Builder negativeExponential() {
         this.negativeExponential = true;
         return this;
      }

      /**
       * Lower cap on the duration (if randomized).
       *
       * @param min Minimum duration.
       * @return Self.
       */
      public Builder min(String min) {
         this.min = io.hyperfoil.util.Util.parseToMillis(min);
         return this;
      }

      /**
       * Upper cap on the duration (if randomized).
       *
       * @param max Maximum duration.
       * @return Self.
       */
      public Builder max(String max) {
         this.max = io.hyperfoil.util.Util.parseToMillis(max);
         return this;
      }

      @Override
      public List<Step> build(SerializableSupplier<Sequence> sequence) {
         return Collections.singletonList(new ScheduleDelayStep(key, type, duration, negativeExponential, min, max));
      }

      /**
       * Alternative way to set delay reference point. See `fromNow` and `fromLast` property setters.
       *
       * @param type Reference point type.
       * @return Self.
       */
      public Builder type(Type type) {
         this.type = type;
         return this;
      }
   }
}
