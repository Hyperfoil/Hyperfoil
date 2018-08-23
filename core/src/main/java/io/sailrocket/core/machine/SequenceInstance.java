package io.sailrocket.core.machine;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class SequenceInstance {
   private static final Logger log = LoggerFactory.getLogger(SequenceInstance.class);
   private static final boolean trace = log.isTraceEnabled();

   private String name;
   private int index;
   private Step[] steps;
   private int currentStep = 0;

   boolean progress(Session session) {
      boolean progressed = false;
      while (currentStep < steps.length) {
         Step step = steps[currentStep];
         if (trace) {
            log.trace("Preparing step {}", step);
         }
         if (step.prepare(session)) {
            if (trace) {
               log.trace("Invoking step {}", step);
            }
            step.invoke(session);
            if (session.currentSequence() != null) {
               ++currentStep;
            } else {
               currentStep = steps.length;
            }
            progressed = true;
         } else {
            if (trace) {
               log.trace("Blocking because of failed prepare");
            }
            return progressed;
         }
      }
      return progressed;
   }

   SequenceInstance reset(String name, int index, Step[] steps) {
      this.name = name;
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
}
