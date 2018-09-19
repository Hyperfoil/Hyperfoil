package io.sailrocket.core.steps;

import io.sailrocket.api.session.Session;
import io.sailrocket.api.config.Step;
import io.sailrocket.core.api.ResourceUtilizer;

public class LoopStep implements Step, ResourceUtilizer {
   private final String counterVar;
   private final int repeats;
   private final String loopedSequence;

   public LoopStep(String counterVar, int repeats, String loopedSequence) {
      this.counterVar = counterVar;
      this.repeats = repeats;
      this.loopedSequence = loopedSequence;
   }

   @Override
   public void invoke(Session session) {
      if (!session.isSet(counterVar)) {
         session.setInt(counterVar, 1);
         session.nextSequence(loopedSequence);
      } else if (session.getInt(counterVar) < 5) {
         session.addToInt(counterVar, 1);
         session.nextSequence(loopedSequence);
      }
   }

   @Override
   public void reserve(Session session) {
      session.declareInt(counterVar);
   }
}
