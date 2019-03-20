package io.hyperfoil.core.steps;

import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.session.ResourceUtilizer;

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
   public boolean invoke(Session session) {
      if (!session.isSet(counterVar)) {
         session.setInt(counterVar, 1);
         session.nextSequence(loopedSequence);
      } else if (session.getInt(counterVar) < repeats) {
         session.addToInt(counterVar, 1);
         session.nextSequence(loopedSequence);
      }
      return true;
   }

   @Override
   public void reserve(Session session) {
      session.declareInt(counterVar);
   }
}
