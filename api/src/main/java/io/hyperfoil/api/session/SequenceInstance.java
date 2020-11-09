package io.hyperfoil.api.session;

import java.util.function.Consumer;

import io.hyperfoil.api.config.Sequence;
import io.hyperfoil.api.config.Step;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

// TODO maybe we should extract an interface
public class SequenceInstance {
   private static final Logger log = LoggerFactory.getLogger(SequenceInstance.class);
   private static final boolean trace = log.isTraceEnabled();

   private Sequence sequence;
   private Consumer<SequenceInstance> releaseHandler;
   private int index;
   private Step[] steps;
   private int currentStep = 0;
   private long blockedTimestamp = Long.MIN_VALUE;
   private int refCnt = 0;

   public boolean progress(Session session) {
      boolean progressed = false;
      while (currentStep < steps.length) {
         Step step = steps[currentStep];
         if (trace) {
            log.trace("#{} {}[{}] invoking step {}", session.uniqueId(), sequence.name(), index, step);
         }
         session.currentSequence(this);
         try {
            if (!step.invoke(session)) {
               if (trace) {
                  log.trace("#{} {}[{}] step {} is blocked", session.uniqueId(), sequence.name(), index, step);
               }
               if (currentStep >= steps.length) {
                  log.warn("#{} Last step reported being blocked but it has also interrupted the sequence.", session.uniqueId());
               }
               return progressed;
            }
            // If session becomes inactive it means that the originally thrown exception was not properly propagated
            if (!session.isActive()) {
               throw SessionStopException.INSTANCE;
            }
         } catch (SessionStopException e) {
            // just rethrow
            throw e;
         } catch (Throwable t) {
            log.error("#{} {}[{}] failure invoking step {}", t, session.uniqueId(), sequence.name(), index, step);
            session.fail(t);
            return false;
         } finally {
            session.currentSequence(null);
         }
         if (currentStep < steps.length) {
            ++currentStep;
         }
         progressed = true;
      }
      return progressed;
   }

   public SequenceInstance reset(Sequence sequence, int index, Step[] steps, Consumer<SequenceInstance> releaseHandler) {
      this.sequence = sequence;
      this.releaseHandler = releaseHandler;
      this.index = index;
      this.steps = steps;
      this.currentStep = 0;
      this.refCnt = 1;
      return this;
   }

   public boolean isCompleted() {
      return currentStep >= steps.length;
   }

   public int index() {
      return index;
   }

   public Sequence definition() {
      return sequence;
   }

   public void setBlockedTimestamp() {
      blockedTimestamp = System.nanoTime();
   }

   public long getBlockedTime() {
      long blockedTimestamp = this.blockedTimestamp;
      if (blockedTimestamp == Long.MIN_VALUE) {
         return 0;
      } else {
         this.blockedTimestamp = Long.MIN_VALUE;
         return System.nanoTime() - blockedTimestamp;
      }
   }

   @Override
   public String toString() {
      return appendTo(new StringBuilder()).toString();
   }

   public StringBuilder appendTo(StringBuilder sb) {
      return sb.append(sequence != null ? sequence.name() : "<none>")
            .append('(').append(index).append(")(")
            .append(currentStep + 1).append('/').append(steps == null ? 0 : steps.length).append(')');
   }

   public void breakSequence(Session session) {
      this.currentStep = steps.length;
      if (trace) {
         log.trace("#{} was interrupted", session.uniqueId());
      }
   }

   public void restart(Session session) {
      log.trace("#{} Restarting current sequence.", session.uniqueId());
      // FIXME: hack - setting this to -1 causes the progress() increment to 0 and start from the beginning
      // Steps cannot use just reset(...) because the increment after a non-blocking step would skip the first
      // step in this sequence.
      this.currentStep = -1;
   }

   public SequenceInstance incRefCnt() {
      refCnt++;
      return this;
   }

   public void decRefCnt(Session session) {
      if (--refCnt == 0) {
         if (trace) {
            log.trace("#{} Releasing sequence {}[{}]", session.uniqueId(), sequence.name(), index);
         }
         releaseHandler.accept(this);
      } else if (trace) {
         // session is null in some mocked tests
         log.trace("#{} Not releasing sequence {}[{}] - refCnt {}",
               session == null ? 0 : session.uniqueId(), sequence == null ? "<noseq>" : sequence.name(), index, refCnt);
      }
   }
}
