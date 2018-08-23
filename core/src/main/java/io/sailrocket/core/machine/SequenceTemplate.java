package io.sailrocket.core.machine;

import java.util.Arrays;

public class SequenceTemplate {
   // debug only
   private final String name;
   private Step[] steps;

   public SequenceTemplate(String name) {
      this.name = name;
   }

   public void addStep(Step step) {
      if (steps == null) {
         steps = new Step[] {step};
      } else {
         steps = Arrays.copyOf(steps, steps.length + 1);
         steps[steps.length - 1] = step;
      }
   }

   void instantiate(Session session, int id) {
      SequenceInstance instance = session.acquireSequence();
      instance.reset(name, id, steps);
      session.enableSequence(instance);
   }

   public void reserve(Session session) {
      for (Step a : steps) {
         if (a instanceof ResourceUtilizer) {
            ((ResourceUtilizer) a).reserve(session);
         }
      }
   }
}
