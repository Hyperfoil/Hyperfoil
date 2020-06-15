package io.hyperfoil.core.steps;

import java.util.Collections;
import java.util.List;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.config.StepBuilder;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.core.session.SessionFactory;

public class LoopStep implements Step, ResourceUtilizer {
   private final Access counterVar;
   private final int repeats;
   private final String sequence;

   public LoopStep(Access counterVar, int repeats, String sequence) {
      this.counterVar = counterVar;
      this.repeats = repeats;
      this.sequence = sequence;
   }

   @Override
   public boolean invoke(Session session) {
      if (!counterVar.isSet(session)) {
         counterVar.setInt(session, 1);
      } else if (counterVar.getInt(session) < repeats) {
         counterVar.addToInt(session, 1);
      }
      session.startSequence(sequence, Session.ConcurrencyPolicy.FAIL);
      return true;
   }

   @Override
   public void reserve(Session session) {
      counterVar.declareInt(session);
   }

   /**
    * Repeats a sequence fixed-number times.
    * <p>
    * This step is supposed to be inserted as the last step of a sequence,
    * and the <code>sequence</code> to be matched with current sequence name.
    * <p>
    * Increments the <code>counterVar</code> and if it is lower than <code>repeats</code>
    * schedules an instance of the <code>sequence</code>.
    */
   @MetaInfServices(StepBuilder.class)
   @Name("loop")
   public static class Builder implements StepBuilder<Builder> {
      private String counterVar;
      private int repeats;
      private String sequence;

      @Override
      public List<Step> build() {
         return Collections.singletonList(new LoopStep(SessionFactory.access(counterVar), repeats, this.sequence));
      }

      /**
       * @param counterVar Variable holding number of iterations.
       * @return Self.
       */
      public Builder counterVar(String counterVar) {
         this.counterVar = counterVar;
         return this;
      }

      /**
       * @param repeats Number of iterations that should be executed.
       * @return Self.
       */
      public Builder repeats(int repeats) {
         this.repeats = repeats;
         return this;
      }

      /**
       * @param sequence Name of the sequence that should be instantiated.
       * @return Self.
       */
      public Builder sequence(String sequence) {
         this.sequence = sequence;
         return this;
      }
   }
}
