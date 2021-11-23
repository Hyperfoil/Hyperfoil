package io.hyperfoil.core.builders;

import io.hyperfoil.api.session.ReadAccess;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.session.SessionFactory;

public class BoolCondition extends BoolConditionBase implements Condition {
   private final ReadAccess fromVar;

   public BoolCondition(ReadAccess fromVar, boolean value) {
      super(value);
      this.fromVar = fromVar;
   }

   @Override
   public boolean test(Session session) {
      Session.Var var = fromVar.getVar(session);
      return testVar(session, var);
   }

   /**
    * Tests session variable containing boolean value.
    */
   public static class Builder<P> extends BoolConditionBuilder<Builder<P>, P> implements Condition.Builder<Builder<P>> {
      private String fromVar;

      public Builder(P parent) {
         super(parent);
      }

      @Override
      public BoolCondition buildCondition() {
         return new BoolCondition(SessionFactory.readAccess(fromVar), value);
      }

      /**
       * Variable name.
       *
       * @param fromVar Variable name.
       * @return Self.
       */
      public Builder<P> fromVar(String fromVar) {
         this.fromVar = fromVar;
         return this;
      }

   }
}
