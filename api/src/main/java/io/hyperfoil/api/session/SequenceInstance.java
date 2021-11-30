package io.hyperfoil.api.session;

import java.util.function.Consumer;

import io.hyperfoil.api.config.Sequence;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.config.StepBuilder;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.message.FormattedMessage;

public class SequenceInstance {
   private static final Logger log = LogManager.getLogger(SequenceInstance.class);
   private static final boolean trace = log.isTraceEnabled();

   private Sequence sequence;
   private Consumer<SequenceInstance> releaseHandler;
   private int index;
   private Step[] steps;
   private int currentStep = 0;
   private int refCnt = 0;

   public boolean progress(Session session) {
      boolean progressed = false;
      while (currentStep < steps.length) {
         Step step = steps[currentStep];
         if (trace) {
            log.trace("#{} {}[{}] invoking step {}", session.uniqueId(), sequence.name(), index, StepBuilder.nameOf(step));
         }
         session.currentSequence(this);
         try {
            if (!step.invoke(session)) {
               if (trace) {
                  log.trace("#{} {}[{}] step {} is blocked", session.uniqueId(), sequence.name(), index, StepBuilder.nameOf(step));
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
            log.error(new FormattedMessage("#{} phase {}, seq {}[{}] failure invoking step {}", session.uniqueId(),
                  session.phase().definition().name(), sequence.name(), index, StepBuilder.nameOf(step)), t);
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

   public boolean isLastStep() {
      return currentStep == steps.length - 1;
   }

   public int index() {
      return index;
   }

   public Sequence definition() {
      return sequence;
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
      assert refCnt > 0;
      if (--refCnt == 0) {
         if (trace) {
            log.trace("#{} Releasing sequence {}[{}]", session.uniqueId(), sequence == null ? "<noseq>" : sequence.name(), index);
         }
         if (releaseHandler != null) {
            releaseHandler.accept(this);
         }
      } else if (trace) {
         // session is null in some mocked tests
         log.trace("#{} Not releasing sequence {}[{}] - refCnt {}",
               session == null ? 0 : session.uniqueId(), sequence == null ? "<noseq>" : sequence.name(), index, refCnt);
      }
   }
}
