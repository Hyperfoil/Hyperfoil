package io.hyperfoil.core.session;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.session.IntAccess;
import io.hyperfoil.api.session.Session;

public class SimpleIntAccess extends SimpleReadAccess implements IntAccess {
   public SimpleIntAccess(Object key) {
      super(key);
   }

   @Override
   public Session.Var createVar(Session session, Session.Var existing) {
      if (existing == null) {
         return new IntVar((SessionImpl) session);
      } else if (existing instanceof IntVar) {
         return existing;
      } else {
         throw new BenchmarkDefinitionException(
               "Variable " + key + " should hold an integer but it is defined to hold an object elsewhere.");
      }
   }

   @Override
   public void setInt(Session session, int value) {
      SessionImpl impl = (SessionImpl) session;
      if (trace) {
         log.trace("#{} {} <- {}", impl.uniqueId(), key, value);
      }
      impl.<IntVar> getVar(index).set(value);
   }

   @Override
   public int addToInt(Session session, int delta) {
      SessionImpl impl = (SessionImpl) session;
      IntVar var = impl.requireSet(index, key);
      int prev = var.intValue(session);
      if (trace) {
         log.trace("#{} {} <- {}", impl.uniqueId(), key, prev + delta);
      }
      var.set(prev + delta);
      return prev;
   }

   @Override
   public void unset(Session session) {
      SessionImpl impl = (SessionImpl) session;
      impl.getVar(index).unset();
   }
}
