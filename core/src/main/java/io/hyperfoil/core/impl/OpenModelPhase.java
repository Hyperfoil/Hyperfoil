package io.hyperfoil.core.impl;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import io.hyperfoil.api.config.Model;
import io.hyperfoil.api.config.Phase;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.impl.rate.FireTimeSequence;
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
 * {@link #proceed(EventExecutorGroup)}</li>
 * <li>when a {@link Session} finishes, {@link #notifyFinished(Session)} can immediately restart it if there are
 * throttled users, preventing it to be pooled</li>
 * </ul>
 * <p>
 * The last point is crucial because it means that a too small amount of pooled sessions would be "compensated" only
 * when a session finished, instead of when {@link #sessionPool} has available sessions available.
 */
final class OpenModelPhase extends PhaseInstanceImpl {

   private final int maxSessions;
   private final AtomicLong throttledUsers = new AtomicLong(0);
   private final FireTimeSequence fireTimeSequence;
   private long nextScheduledFireTimeNs;

   OpenModelPhase(FireTimeSequence fireTimeSequence, Phase def, String runId, int agentId) {
      super(def, runId, agentId);
      this.fireTimeSequence = fireTimeSequence;
      this.maxSessions = Math.max(1, def.benchmark().slice(((Model.OpenModel) def.model).maxSessions, agentId));
      this.nextScheduledFireTimeNs = fireTimeSequence.nextFireTimeNs();
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
      long remainingNsToFirstFireTime = nextScheduledFireTimeNs - elapsedNs;
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
      long elapsedTimeNs = System.nanoTime() - nanoTimeStart;
      if (elapsedTimeNs < nextScheduledFireTimeNs) {
         log.warn("{}: proceed() called before fire time: elapsed={} ns, nextFireTime={} ns",
               def.name, elapsedTimeNs, nextScheduledFireTimeNs);
         executorGroup.schedule(() -> proceed(executorGroup),
               nextScheduledFireTimeNs - elapsedTimeNs, TimeUnit.NANOSECONDS);
         return;
      }
      // Handle the current fire time
      long fireTimeNs = nextScheduledFireTimeNs;
      long absoluteStartTimeMs = absoluteStartTime + TimeUnit.NANOSECONDS.toMillis(fireTimeNs);
      long absoluteStartNanoTime = nanoTimeStart + fireTimeNs;
      if (!startNewSession(absoluteStartTimeMs, absoluteStartNanoTime)) {
         throttledUsers.incrementAndGet();
      }
      // Compute next fire time and schedule accordingly
      nextScheduledFireTimeNs = fireTimeSequence.nextFireTimeNs();
      long nowNs = System.nanoTime() - nanoTimeStart;
      long delayNs = nextScheduledFireTimeNs - nowNs;
      if (delayNs <= 0) {
         executorGroup.execute(() -> proceed(executorGroup));
      } else {
         if (trace) {
            log.trace("{}: {} ns after start, next fire in {} ns ({} throttled)",
                  def.name, nowNs, delayNs, throttledUsers);
         }
         executorGroup.schedule(() -> proceed(executorGroup), delayNs, TimeUnit.NANOSECONDS);
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
               session.start(-1, -1, this);
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
