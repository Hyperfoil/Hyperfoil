package io.hyperfoil.core.steps;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.InitFromParam;
import io.hyperfoil.api.config.Locator;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.config.StepBuilder;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.config.BaseSequenceBuilder;
import io.hyperfoil.core.builders.BaseStepBuilder;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.core.util.Unique;
import io.hyperfoil.function.SerializableToLongFunction;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class ScheduleDelayStep implements Step, ResourceUtilizer {
   private static final Logger log = LoggerFactory.getLogger(ScheduleDelayStep.class);

   private final Access key;
   private final Type type;
   private final SerializableToLongFunction<Session> duration;

   public ScheduleDelayStep(Access key, Type type, SerializableToLongFunction<Session> duration) {
      this.key = key;
      this.type = type;
      this.duration = duration;
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
      long duration = this.duration.applyAsLong(session);
      blockedUntil.timestamp = baseTimestamp + duration;
      long delay = blockedUntil.timestamp - now;
      if (delay > 0) {
         log.trace("Scheduling #{} to run in {}", session.uniqueId(), delay);
         session.executor().schedule((Callable<?>) session, delay, TimeUnit.MILLISECONDS);
      } else {
         log.trace("Continuing, duration {} resulted in delay {}", duration, delay);
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

   public enum RandomType {
      /**
       * Do not randomize; use constant duration.
       */
      CONSTANT,
      /**
       * Use linearly random duration between <code>min</code> and <code>max</code> (inclusively).
       */
      LINEAR,
      /**
       * Use negative-exponential duration with expected value of <code>duration</code>, capped at <code>min</code>
       * and <code>max</code> (inclusively).
       */
      NEGATIVE_EXPONENTIAL
   }

   static class Timestamp {
      long timestamp = Long.MAX_VALUE;
   }

   /**
    * Define a point in future until which we should wait. Does not cause waiting.
    */
   @MetaInfServices(StepBuilder.class)
   @Name("scheduleDelay")
   public static class Builder extends BaseStepBuilder<Builder> {
      protected Object key;
      protected Access keyAccess;
      private long duration;
      private Type type = Type.FROM_NOW;
      private RandomType randomType = RandomType.CONSTANT;
      private long min = 0;
      private long max = Long.MAX_VALUE;

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

      public Builder duration(long duration, TimeUnit timeUnit) {
         this.duration = timeUnit == null ? 0 : timeUnit.toMillis(duration);
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
       * Randomize the duration.
       *
       * @param randomType Type of random generator.
       * @return Self.
       */
      public Builder random(RandomType randomType) {
         this.randomType = randomType;
         return this;
      }

      public Builder min(long min, TimeUnit timeUnit) {
         this.min = timeUnit.toMillis(min);
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

      public Builder max(long max, TimeUnit timeUnit) {
         this.max = timeUnit.toMillis(max);
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
      public void prepareBuild() {
         if (key == null) {
            throw new BenchmarkDefinitionException("Key was not defined.");
         }
         keyAccess = SessionFactory.access(key);
      }

      @Override
      public List<Step> build() {
         long duration = this.duration;
         long min = this.min;
         long max = this.max;
         SerializableToLongFunction<Session> func;
         switch (randomType) {
            case CONSTANT:
               if (this.min != 0 || this.max != Long.MAX_VALUE) {
                  throw new BenchmarkDefinitionException("This duration should be constant; no need to define 'min' and 'max'.");
               } else if (this.duration <= 0) {
                  throw new BenchmarkDefinitionException("Duration must be positive.");
               }
               func = new ConstantDuration(duration);
               break;
            case LINEAR:
               if (this.duration != 0) {
                  throw new BenchmarkDefinitionException("The duration is set through 'min' and 'max'; do not use 'duration'");
               } else if (this.min < 0) {
                  throw new BenchmarkDefinitionException("The minimum duration must not be lower than 0.");
               } else if (this.max > TimeUnit.HOURS.toMillis(24)) {
                  throw new BenchmarkDefinitionException("The maximum duration is over 24 hours: that's likely an error.");
               }
               func = new RandomLinearDuration(min, max);
               break;
            case NEGATIVE_EXPONENTIAL:
               func = new RandomNegExpDuration(duration, max, min);
               break;
            default:
               throw new BenchmarkDefinitionException("Unknown randomness type: " + randomType);
         }
         return Collections.singletonList(new ScheduleDelayStep(keyAccess, type, func));
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

   /**
    * Block current sequence for specified duration.
    */
   @MetaInfServices(StepBuilder.class)
   @Name("thinkTime")
   public static class ThinkTimeBuilder extends Builder implements InitFromParam<ThinkTimeBuilder> {
      @Override
      public ThinkTimeBuilder addTo(BaseSequenceBuilder parent) {
         return (ThinkTimeBuilder) super.addTo(parent);
      }

      @Override
      public void prepareBuild() {
         key = new Unique();
         if (Locator.current().sequence().rootSequence().concurrency() > 0) {
            keyAccess = SessionFactory.sequenceScopedAccess(key);
         } else {
            keyAccess = SessionFactory.access(key);
         }
      }

      @Override
      public List<Step> build() {
         return Arrays.asList(super.build().get(0), new AwaitDelayStep(keyAccess));
      }

      /**
       * @param param Duration of the delay with appropriate suffix (e.g. `ms` or `s`).
       * @return Self.
       */
      @Override
      public ThinkTimeBuilder init(String param) {
         duration(param);
         return this;
      }
   }

   private static class ConstantDuration implements SerializableToLongFunction<Session> {
      private final long duration;

      public ConstantDuration(long duration) {
         this.duration = duration;
      }

      @Override
      public long applyAsLong(Session session) {
         return duration;
      }
   }

   private static class RandomLinearDuration implements SerializableToLongFunction<Session> {
      private final long min;
      private final long max;

      public RandomLinearDuration(long min, long max) {
         this.min = min;
         this.max = max;
      }

      @Override
      public long applyAsLong(Session session) {
         return ThreadLocalRandom.current().nextLong(min, max + 1);
      }
   }

   private static class RandomNegExpDuration implements SerializableToLongFunction<Session> {
      private final long duration;
      private final long max;
      private final long min;

      public RandomNegExpDuration(long duration, long max, long min) {
         this.duration = duration;
         this.max = max;
         this.min = min;
      }

      @Override
      public long applyAsLong(Session session) {
         double rand = ThreadLocalRandom.current().nextDouble();
         long delay = (long) (duration * -Math.log(Math.max(rand, 1e-20d)));
         return Math.max(Math.min(delay, max), min);
      }
   }
}
