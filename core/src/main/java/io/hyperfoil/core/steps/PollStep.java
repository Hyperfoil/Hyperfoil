package io.hyperfoil.core.steps;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import io.hyperfoil.api.config.Sequence;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.config.BaseSequenceBuilder;
import io.hyperfoil.core.builders.BaseStepBuilder;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.function.SerializableSupplier;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class PollStep<T> implements Step, ResourceUtilizer {
   private static final Logger log = LoggerFactory.getLogger(PollStep.class);

   private final Function<Session, T> provider;
   private final Access toVar;
   private final BiPredicate<Session, T> filter;
   private final BiConsumer<Session, T> recycler;
   private final long periodMs;
   private final int maxRetries;

   public PollStep(Function<Session, T> provider, String toVar, BiPredicate<Session, T> filter, BiConsumer<Session, T> recycler, long periodMs, int maxRetries) {
      this.provider = provider;
      this.filter = filter;
      this.toVar = SessionFactory.access(toVar);
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
            session.executor().schedule((Runnable) session, periodMs, TimeUnit.MILLISECONDS);
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
      session.executor().schedule((Runnable) session, periodMs, TimeUnit.MILLISECONDS);
      return false;
   }

   @Override
   public void reserve(Session session) {
      toVar.declareObject(session);
   }

   public static class Builder<T> extends BaseStepBuilder {
      private final Function<Session, T> provider;
      private final String var;
      private BiPredicate<Session, T> filter = (s, o) -> true;
      private BiConsumer<Session, T> recycler;
      private long periodMs = 50;
      private int maxRetries = 16;

      public Builder(BaseSequenceBuilder parent, Function<Session, T> provider, String var) {
         super(parent);
         this.provider = provider;
         this.var = var;
      }

      public Builder<T> filter(BiPredicate<Session, T> filter, BiConsumer<Session, T> recycler) {
         this.filter = Objects.requireNonNull(filter);
         this.recycler = Objects.requireNonNull(recycler);
         return this;
      }

      public Builder<T> filter(Predicate<T> filter, Consumer<T> recycler) {
         Objects.requireNonNull(filter);
         Objects.requireNonNull(recycler);
         this.filter = (s, ship) -> filter.test(ship);
         this.recycler = (s, ship) -> recycler.accept(ship);
         return this;
      }

      public Builder<T> periodMs(long periodMs) {
         this.periodMs = periodMs;
         return this;
      }

      public Builder<T> maxRetries(int maxRetries) {
         this.maxRetries = maxRetries;
         return this;
      }

      @Override
      public List<Step> build(SerializableSupplier<Sequence> sequence) {
         return Collections.singletonList(new PollStep<>(provider, var, filter, recycler, periodMs, maxRetries));
      }
   }
}
