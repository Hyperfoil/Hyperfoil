package io.hyperfoil.core.steps;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.session.ObjectAccess;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.builders.BaseStepBuilder;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.function.SerializableBiConsumer;
import io.hyperfoil.function.SerializableBiPredicate;
import io.hyperfoil.function.SerializableConsumer;
import io.hyperfoil.function.SerializableFunction;
import io.hyperfoil.function.SerializablePredicate;

public class PollStep<T> implements Step {
   private static final Logger log = LogManager.getLogger(PollStep.class);

   private final SerializableFunction<Session, T> provider;
   private final ObjectAccess toVar;
   private final SerializableBiPredicate<Session, T> filter;
   private final SerializableBiConsumer<Session, T> recycler;
   private final long periodMs;
   private final int maxRetries;

   public PollStep(SerializableFunction<Session, T> provider, ObjectAccess toVar, SerializableBiPredicate<Session, T> filter,
         SerializableBiConsumer<Session, T> recycler, long periodMs, int maxRetries) {
      this.provider = provider;
      this.filter = filter;
      this.toVar = toVar;
      this.recycler = recycler;
      this.periodMs = periodMs;
      this.maxRetries = maxRetries;
   }

   @Override
   public boolean invoke(Session session) {
      for (int i = 0; i < maxRetries; ++i) {
         T object = provider.apply(session);
         if (object == null) {
            // Note: it's possible that we'll try to poll earlier
            log.trace("Did not fetch object, scheduling #{} in {}", session.uniqueId(), periodMs);
            session.executor().schedule(session.runTask(), periodMs, TimeUnit.MILLISECONDS);
            return false;
         } else if (filter.test(session, object)) {
            toVar.setObject(session, object);
            return true;
         } else {
            recycler.accept(session, object);
         }
      }
      // We did not have an accepting match
      log.trace("Not accepted, scheduling #{} in {}", session.uniqueId(), periodMs);
      session.executor().schedule(session.runTask(), periodMs, TimeUnit.MILLISECONDS);
      return false;
   }

   /**
    * Periodically tries to insert object into session variable.
    */
   public static class Builder<T> extends BaseStepBuilder<Builder<T>> {
      private final SerializableFunction<Session, T> provider;
      private final String var;
      private SerializableBiPredicate<Session, T> filter = (s, o) -> true;
      private SerializableBiConsumer<Session, T> recycler;
      private long periodMs = 50;
      private int maxRetries = 16;

      public Builder(SerializableFunction<Session, T> provider, String var) {
         this.provider = provider;
         this.var = var;
      }

      public Builder<T> filter(SerializableBiPredicate<Session, T> filter, SerializableBiConsumer<Session, T> recycler) {
         this.filter = Objects.requireNonNull(filter);
         this.recycler = Objects.requireNonNull(recycler);
         return this;
      }

      public Builder<T> filter(SerializablePredicate<T> filter, SerializableConsumer<T> recycler) {
         Objects.requireNonNull(filter);
         Objects.requireNonNull(recycler);
         this.filter = (s, ship) -> filter.test(ship);
         this.recycler = (s, ship) -> recycler.accept(ship);
         return this;
      }

      /**
       * Polling period.
       *
       * @param periodMs Period in milliseconds.
       * @return Self.
       */
      public Builder<T> periodMs(long periodMs) {
         this.periodMs = periodMs;
         return this;
      }

      /**
       * Maximum number of retries before giving up (and waiting for next period).
       *
       * @param maxRetries Number of retries.
       * @return Self.
       */
      public Builder<T> maxRetries(int maxRetries) {
         this.maxRetries = maxRetries;
         return this;
      }

      @Override
      public List<Step> build() {
         return Collections.singletonList(
               new PollStep<>(provider, SessionFactory.objectAccess(var), filter, recycler, periodMs, maxRetries));
      }
   }
}
