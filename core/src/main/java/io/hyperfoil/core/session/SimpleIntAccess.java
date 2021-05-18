package io.hyperfoil.core.session;

import io.hyperfoil.api.session.IntAccess;
import io.hyperfoil.api.session.Session;

public class SimpleIntAccess extends SimpleReadAccess implements IntAccess {
   public SimpleIntAccess(Object key) {
      super(key);
   }

   @Override
   public void reserve(Session session) {
      ((SessionImpl) session).reserveIntVar(key);
   }

   @Override
   public void setInt(Session session, int value) {
      SessionImpl impl = (SessionImpl) session;
      if (trace) {
         log.trace("#{} {} <- {}", impl.uniqueId(), key, value);
      }
      impl.<IntVar>getVar(key).set(value);
   }

   @Override
   public int addToInt(Session session, int delta) {
      SessionImpl impl = (SessionImpl) session;
      IntVar var = impl.requireSet(key);
      int prev = var.get();
      if (trace) {
         log.trace("#{} {} <- {}", impl.uniqueId(), key, prev + delta);
      }
      var.set(prev + delta);
      return prev;
   }

   @Override
   public void unset(Session session) {
      SessionImpl impl = (SessionImpl) session;
      impl.getVar(key).unset();
   }
}
