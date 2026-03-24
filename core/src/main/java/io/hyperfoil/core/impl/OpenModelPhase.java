package io.hyperfoil.core.impl;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import io.hyperfoil.api.config.Model;
import io.hyperfoil.api.config.Phase;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.impl.rate.FireTimeSequence;

/**
 * This is a base class for Open Model phases that need to compensate users based on the available ones in the session pool.
 * <br>
 * The notion of being "throttled" users requires some explanation:
 * <ul>
 * <li>a user requires a {@link Session} to run, hence the two terms are used to denote the same concept</li>
 * <li>starting a {@link Session} doesn't mean immediate execution, but scheduling a deferred start in the
 * {@link Session#executor()}</li>
 * <li>given that {@link Session}s are pooled, being throttled means that no available instances are found by
 * {@link #proceed()}</li>
 * <li>when a {@link Session} finishes, {@link #notifyFinished(Session)} can immediately restart it if there are
 * throttled users, preventing it to be pooled</li>
 * </ul>
 * <p>
 * When catching up throttled users, the finishing session is released back to the pool and a new session acquisition
 * is scheduled on the {@link #executorGroup} (which round-robins across event loops). This ensures catch-up sessions
 * are distributed evenly across all event loops, preventing a single core from being saturated (see issue #627),
 * while preserving progress guarantees since {@code acquire()} can still work-steal from other event loops.
 */
final class OpenModelPhase extends PhaseInstanceImpl {

   private final int maxSessions;
   private final AtomicLong throttledUsers = new AtomicLong(0);
   private final FireTimeSequence fireTimeSequence;
   private final Runnable proceedTask = this::proceed;
   private final Runnable catchUpTask = this::catchUp;
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
   protected void proceed() {
      if (status.isFinished()) {
         return;
      }
      long elapsedTimeNs = System.nanoTime() - nanoTimeStart;
      if (elapsedTimeNs < nextScheduledFireTimeNs) {
         // this can happen on PhaseInstanceImpl::start once RUNNING
         executorGroup.schedule(proceedTask,
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
         executorGroup.execute(proceedTask);
      } else {
         if (trace) {
            log.trace("{}: {} ns after start, next fire in {} ns ({} throttled)",
                  def.name, nowNs, delayNs, throttledUsers);
         }
         executorGroup.schedule(proceedTask, delayNs, TimeUnit.NANOSECONDS);
      }
   }

   @Override
   public void notifyFinished(Session session) {
      if (session != null && !status.isFinished()) {
         long throttled = throttledUsers.get();
         while (throttled != 0) {
            if (throttledUsers.compareAndSet(throttled, throttled - 1)) {
               // Release the session back to the pool and schedule a new session acquisition
               // on the executorGroup, which round-robins across event loops. This distributes
               // catch-up load evenly while preserving progress guarantees (acquire() can still
               // work-steal from other event loops if the local queue is empty).
               super.notifyFinished(session);
               executorGroup.execute(catchUpTask);
               return;
            } else {
               throttled = throttledUsers.get();
            }
         }
      }
      super.notifyFinished(session);
   }

   private void catchUp() {
      if (!startNewSession(-1, -1)) {
         // Session acquisition failed (e.g., the released session was grabbed by proceed()
         // before we ran). Re-increment so the user is not silently lost.
         throttledUsers.incrementAndGet();
      }
   }

}
