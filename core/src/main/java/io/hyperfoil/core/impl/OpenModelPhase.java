package io.hyperfoil.core.impl;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import io.hyperfoil.api.config.Model;
import io.hyperfoil.api.config.Phase;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.impl.rate.FireTimeListener;
import io.hyperfoil.core.impl.rate.RateGenerator;
import io.netty.util.concurrent.EventExecutorGroup;

/**
 * This is a base class for Open Model phases that need to compensate users based on the available ones in the session pool.
 * <br>
 * The notion of being "throttled" users requires some explanation:
 * <ul>
 * <li>a user requires a {@link Session} to run, hence the two terms are used to denote the same concept</li>
 * <li>starting a {@link Session} doesn't mean immediate execution, but scheduling a deferred start in the
 * {@link Session#executor()}</li>
 * <li>given that {@link Session}s are pooled, being throttled means that no available instances are found by
 * {@link #onFireTimes(long)}</li>
 * <li>when a {@link Session} finishes, {@link #notifyFinished(Session)} can immediately restart it if there are
 * throttled users, preventing it to be pooled</li>
 * </ul>
 * <p>
 * The last point is crucial because it means that a too small amount of pooled sessions would be "compensated" only
 * when a session finished, instead of when {@link #sessionPool} has available sessions available.
 */
final class OpenModelPhase extends PhaseInstanceImpl implements FireTimeListener {

   private final int maxSessions;
   private final AtomicLong throttledUsers = new AtomicLong(0);
   private final RateGenerator rateGenerator;
   private final long relativeFirstFireTimeNs;

   OpenModelPhase(RateGenerator rateGenerator, Phase def, String runId, int agentId) {
      super(def, runId, agentId);
      this.rateGenerator = rateGenerator;
      this.maxSessions = Math.max(1, def.benchmark().slice(((Model.OpenModel) def.model).maxSessions, agentId));
      this.relativeFirstFireTimeNs = rateGenerator.lastComputedFireTimeNs();
   }

   @Override
   public void reserveSessions() {
      if (log.isDebugEnabled()) {
         log.debug("Phase {} reserving {} sessions", def.name, maxSessions);
      }
      sessionPool.reserve(maxSessions);
   }

   @Override
   protected void proceedOnStarted(final EventExecutorGroup executorGroup) {
      long elapsedNs = System.nanoTime() - nanoTimeStart;
      long remainingNsToFirstFireTime = relativeFirstFireTimeNs - elapsedNs;
      if (remainingNsToFirstFireTime > 0) {
         executorGroup.schedule(() -> proceed(executorGroup), remainingNsToFirstFireTime, TimeUnit.NANOSECONDS);
      } else {
         proceed(executorGroup);
      }
   }

   @Override
   public void proceed(final EventExecutorGroup executorGroup) {
      if (status.isFinished()) {
         return;
      }
      long realFireTimeNs = System.nanoTime();
      long elapsedTimeNs = realFireTimeNs - nanoTimeStart;
      // the time should flow forward: we can have some better check here for NTP and maybe rise a warning
      assert elapsedTimeNs >= rateGenerator.lastComputedFireTimeNs();
      long expectedNextFireTimeNs = rateGenerator.computeNextFireTime(elapsedTimeNs, this);
      // we need to make sure that the scheduling decisions are made based on the current time
      long rateGenerationDelayNs = System.nanoTime() - realFireTimeNs;
      assert rateGenerationDelayNs >= 0;
      // given that both computeNextFireTime and onFireTimes can take some time, we need to adjust the fire time
      long scheduledFireDelayNs = Math.max(0, (expectedNextFireTimeNs - elapsedTimeNs) - rateGenerationDelayNs);
      if (trace) {
         log.trace("{}: {} ns after start, {} started ({} throttled), next user in {} ns, scheduling decisions took {} ns",
               def.name, elapsedTimeNs,
               rateGenerator.fireTimes(), throttledUsers, scheduledFireDelayNs, rateGenerationDelayNs);
      }
      if (scheduledFireDelayNs <= 0) {
         // we're so late that there's no point in bothering the executor with timers
         executorGroup.execute(() -> proceed(executorGroup));
      } else {
         executorGroup.schedule(() -> proceed(executorGroup), scheduledFireDelayNs, TimeUnit.NANOSECONDS);
      }
   }

   @Override
   public void onFireTime() {
      if (!startNewSession()) {
         throttledUsers.incrementAndGet();
      }
   }

   @Override
   public void notifyFinished(Session session) {
      if (session != null && !status.isFinished()) {
         long throttled = throttledUsers.get();
         while (throttled != 0) {
            if (throttledUsers.compareAndSet(throttled, throttled - 1)) {
               // TODO: it would be nice to compensate response times
               // in these invocations for the fact that we're applying
               // SUT feedback, but that would be imprecise anyway.
               session.start(this);
               // this prevents the session to be pooled
               return;
            } else {
               throttled = throttledUsers.get();
            }
         }
      }
      super.notifyFinished(session);
   }

}
