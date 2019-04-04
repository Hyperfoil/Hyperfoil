package io.hyperfoil.core.steps;

import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.core.session.SessionFactory;

public class LoopStep implements Step, ResourceUtilizer {
   private final Access counterVar;
   private final int repeats;
   private final String loopedSequence;

   public LoopStep(String counterVar, int repeats, String loopedSequence) {
      this.counterVar = SessionFactory.access(counterVar);
      this.repeats = repeats;
      this.loopedSequence = loopedSequence;
   }

   @Override
   public boolean invoke(Session session) {
      if (!counterVar.isSet(session)) {
         counterVar.setInt(session, 1);
         session.nextSequence(loopedSequence);
      } else if (counterVar.getInt(session) < repeats) {
         counterVar.addToInt(session, 1);
         session.nextSequence(loopedSequence);
      }
      return true;
   }

   @Override
   public void reserve(Session session) {
      counterVar.declareInt(session);
   }
}
