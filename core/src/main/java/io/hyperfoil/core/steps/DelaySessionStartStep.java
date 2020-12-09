package io.hyperfoil.core.steps;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import io.hyperfoil.api.config.Phase;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.session.Session;
import io.netty.util.concurrent.ScheduledFuture;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class DelaySessionStartStep implements Step, ResourceUtilizer {
   private static final Logger log = LoggerFactory.getLogger(DelaySessionStartStep.class);
   public static final Session.ResourceKey<Holder> KEY = new Session.ResourceKey<Holder>() {};

   private final String[] sequences;
   private final double targetRate;
   private final double targetRateIncrement;
   private final boolean randomize;

   public DelaySessionStartStep(String[] sequences, double targetRate, double targetRateIncrement, boolean randomize) {
      this.sequences = sequences;
      this.targetRate = targetRate;
      this.targetRateIncrement = targetRateIncrement;
      this.randomize = randomize;
   }

   @Override
   public boolean invoke(Session session) {
      Holder holder = session.getResource(KEY);
      if (holder.phase != session.phase()) {
         holder.iteration = 0;
         holder.startTimeWithOffset = Long.MIN_VALUE;
         holder.phase = session.phase();
      }
      if (holder.startTimeWithOffset == Long.MIN_VALUE) {
         int users = ((Phase.Always) session.phase()).users;
         double targetRate = this.targetRate + this.targetRateIncrement * session.phase().iteration;
         holder.period = users * 1000 / targetRate;
         holder.startTimeWithOffset = session.phaseStartTimestamp();
         if (randomize && holder.period >= 1) {
            holder.startTimeWithOffset += ThreadLocalRandom.current().nextLong((long) holder.period);
         }
      }
      long now = System.currentTimeMillis();
      long next = holder.startTimeWithOffset + (long) (holder.iteration * holder.period);
      if (now < next) {
         if (holder.future == null) {
            log.trace("#{} scheduling in {} ms", session.uniqueId(), next - now);
            holder.future = session.executor().schedule(session, next - now, TimeUnit.MILLISECONDS);
         }
         return false;
      }
      holder.future = null;
      holder.iteration++;
      for (String sequence : sequences) {
         session.startSequence(sequence, false, Session.ConcurrencyPolicy.FAIL);
      }
      return true;
   }

   @Override
   public void reserve(Session session) {
      session.declareResource(KEY, Holder::new, true);
   }

   public static class Holder implements Session.Resource {
      public int iteration = 0;
      public long startTimeWithOffset = Long.MIN_VALUE;
      public double period;
      public ScheduledFuture<Void> future;
      public Phase phase;

      public long lastStartTime() {
         return startTimeWithOffset + (long) ((iteration - 1) * period);
      }

      @Override
      public void onSessionReset(Session session) {
         if (future != null) {
            future.cancel(false);
            future = null;
         }
      }
   }
}
