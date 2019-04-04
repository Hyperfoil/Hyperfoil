package io.hyperfoil.core.steps;

import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.core.session.SessionFactory;

public class AwaitVarStep extends DependencyStep {
   public AwaitVarStep(String var) {
      super(null, new Access[] {access(var)});
   }

   private static Access access(String var) {
      if (var.startsWith("!")) {
         return new NegatedAccess(SessionFactory.access(var.substring(1).trim()));
      }
      return SessionFactory.access(var);
   }

   public static class NegatedAccess implements Access {
      private final Access access;

      public NegatedAccess(Access access) {
         this.access = access;
      }

      @Override
      public void declareObject(Session session) {
         access.declareObject(session);
      }

      @Override
      public void declareInt(Session session) {
         access.declareInt(session);
      }

      @Override
      public boolean isSet(Session session) {
         return !access.isSet(session);
      }

      @Override
      public Object getObject(Session session) {
         throw new UnsupportedOperationException();
      }

      @Override
      public void setObject(Session session, Object value) {
         throw new UnsupportedOperationException();
      }

      @Override
      public int getInt(Session session) {
         throw new UnsupportedOperationException();
      }

      @Override
      public void setInt(Session session, int value) {
         throw new UnsupportedOperationException();
      }

      @Override
      public <V extends Session.Var> V getVar(Session Session) {
         throw new UnsupportedOperationException();
      }

      @Override
      public int addToInt(Session session, int delta) {
         throw new UnsupportedOperationException();
      }

      @Override
      public Object activate(Session session) {
         throw new UnsupportedOperationException();
      }

      @Override
      public void unset(Session session) {
         throw new UnsupportedOperationException();
      }
   }
}
