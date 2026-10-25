package io.hyperfoil.core.steps;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.function.SerializableToLongFunction;

public class ScheduleDelayStepTest {

   private static final long MIN_NS = TimeUnit.SECONDS.toNanos(1);
   private static final long MAX_NS = TimeUnit.SECONDS.toNanos(2);

   @Test
   public void testLinearMinMaxAreInNanoseconds() {
      ScheduleDelayStep.Builder builder = new ScheduleDelayStep.Builder()
            .key("foo").random(ScheduleDelayStep.RandomType.LINEAR).min("1s").max("2s");
      SerializableToLongFunction<Session> duration = durationFunction(builder);
      for (int i = 0; i < 500; ++i) {
         assertThat(duration.applyAsLong(null)).isBetween(MIN_NS, MAX_NS);
      }
   }

   @Test
   public void testLinearMinMaxWithTimeUnitAreInNanoseconds() {
      ScheduleDelayStep.Builder builder = new ScheduleDelayStep.Builder()
            .key("foo").random(ScheduleDelayStep.RandomType.LINEAR).min(1, TimeUnit.SECONDS).max(2, TimeUnit.SECONDS);
      SerializableToLongFunction<Session> duration = durationFunction(builder);
      for (int i = 0; i < 500; ++i) {
         assertThat(duration.applyAsLong(null)).isBetween(MIN_NS, MAX_NS);
      }
   }

   @Test
   public void testNegativeExponentialCapsAreInNanoseconds() {
      ScheduleDelayStep.Builder builder = new ScheduleDelayStep.Builder()
            .key("foo").random(ScheduleDelayStep.RandomType.NEGATIVE_EXPONENTIAL).duration("1500ms").min("1s").max("2s");
      SerializableToLongFunction<Session> duration = durationFunction(builder);
      for (int i = 0; i < 500; ++i) {
         assertThat(duration.applyAsLong(null)).isBetween(MIN_NS, MAX_NS);
      }
   }

   @SuppressWarnings("unchecked")
   private static SerializableToLongFunction<Session> durationFunction(ScheduleDelayStep.Builder builder) {
      Step step = builder.build().get(0);
      try {
         java.lang.reflect.Field field = ScheduleDelayStep.class.getDeclaredField("duration");
         field.setAccessible(true);
         return (SerializableToLongFunction<Session>) field.get(step);
      } catch (ReflectiveOperationException e) {
         throw new AssertionError(e);
      }
   }
}
