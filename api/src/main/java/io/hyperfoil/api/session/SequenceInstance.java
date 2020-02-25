package io.hyperfoil.api.session;

import io.hyperfoil.api.config.Step;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

// TODO maybe we should extract an interface
public class SequenceInstance {
   private static final Logger log = LoggerFactory.getLogger(SequenceInstance.class);
   private static final boolean trace = log.isTraceEnabled();

   private String name;
   private int sourceId;
   private int index;
   private Step[] steps;
   private int currentStep = 0;
   private long blockedTimestamp = Long.MIN_VALUE;

   public boolean progress(Session session) {
      boolean progressed = false;
      while (currentStep < steps.length) {
         Step step = steps[currentStep];
         if (trace) {
            log.trace("#{} {}({}) invoking step {}", session.uniqueId(), name, index, step);
         }
         try {
            if (!step.invoke(session)) {
               if (trace) {
                  log.trace("#{} {}({}) step {} is blocked", session.uniqueId(), name, index, step);
               }
               if (session.currentSequence() == null) {
                  log.warn("#{} Last step reported being blocked but it has also interrupted the sequence.", session.uniqueId());
                  currentStep = steps.length;
               }
               return progressed;
            }
         } catch (SessionStopException e) {
            // just rethrow
            throw e;
         } catch (Throwable t) {
            log.error("#{} {}({}) failure invoking step {}", t, session.uniqueId(), name, index, step);
            session.fail(t);
            return false;
         }
         if (session.currentSequence() != null) {
            ++currentStep;
         } else {
            currentStep = steps.length;
            if (trace) {
               log.trace("#{} was interrupted", session.uniqueId());
            }
         }
         progressed = true;
      }
      return progressed;
   }

   public SequenceInstance reset(String name, int sourceId, int index, Step[] steps) {
      this.name = name;
      this.sourceId = sourceId;
      this.index = index;
      this.steps = steps;
      this.currentStep = 0;
      return this;
   }

   public boolean isCompleted() {
      return currentStep >= steps.length;
   }

   public int index() {
      return index;
   }

   public String name() {
      return name;
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
      return sb.append(name).append('(').append(index).append(")(").append(currentStep + 1).append('/').append(steps == null ? 0 : steps.length).append(')');
   }
}
