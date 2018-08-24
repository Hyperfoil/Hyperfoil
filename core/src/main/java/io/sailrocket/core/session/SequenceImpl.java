package io.sailrocket.core.session;

import java.util.Arrays;

import io.sailrocket.api.Sequence;
import io.sailrocket.api.SequenceInstance;
import io.sailrocket.api.Session;
import io.sailrocket.api.Step;
import io.sailrocket.core.api.ResourceUtilizer;

public class SequenceImpl implements Sequence {
   // debug only
   private final String name;
   private Step[] steps;

   public SequenceImpl(String name) {
      this(name, null);
   }

   public SequenceImpl(String name, Step[] steps) {
      this.name = name;
      this.steps = steps;
   }

   @Override
   public Sequence step(Step step) {
      if (steps == null) {
         steps = new Step[] {step};
      } else {
         steps = Arrays.copyOf(steps, steps.length + 1);
         steps[steps.length - 1] = step;
      }
      return this;
   }

   @Override
   public void instantiate(Session session, int id) {
      SessionImpl impl = (SessionImpl) session;
      SequenceInstance instance = impl.acquireSequence();
      instance.reset(name, id, steps);
      impl.enableSequence(instance);
   }

   @Override
   public void reserve(Session session) {
      for (Step a : steps) {
         if (a instanceof ResourceUtilizer) {
            ((ResourceUtilizer) a).reserve(session);
         }
      }
   }
}
