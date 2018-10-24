package io.sailrocket.api.session;

import io.sailrocket.api.statistics.Statistics;
import io.sailrocket.api.config.Step;
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

   public boolean progress(Session session) {
      boolean progressed = false;
      while (currentStep < steps.length) {
         Step step = steps[currentStep];
         if (trace) {
            log.trace("#{} {} invoking step {}", session.uniqueId(), name, step);
         }
         try {
            if (!step.invoke(session)) {
               log.trace("#{} {} step {} is blocked", session.uniqueId(), name, step);
               return progressed;
            }
         } catch (Throwable t) {
            log.error("{} {} failure invoking step {}", t, session.uniqueId(), name, step);
            session.fail(t);
            return false;
         }
         if (session.currentSequence() != null) {
            ++currentStep;
         } else {
            currentStep = steps.length;
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
      return  currentStep >= steps.length;
   }

   public int index() {
      return index;
   }

   @Override
   public String toString() {
      return name + "(" + currentStep + "/" + steps.length + ")";
   }

   public Statistics statistics(Session session) {
      return session.statistics(sourceId);
   }
}
