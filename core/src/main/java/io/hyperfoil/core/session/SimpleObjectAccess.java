package io.hyperfoil.core.session;

import io.hyperfoil.api.session.ObjectAccess;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.util.Util;

// TODO: In the future it would be great if we could just hold an index into Session and do array access
// instead of map lookup by name. However that would require us to know all Access instances ahead;
// we cannot get them through .declareObject()/.declareInt() as this happens on a live session and Access
// objects are not bound to any session. Steps (and other scenario objects) could implement an interface
// to provide all Access objects but often we hold just functions and that would prohibit use of lambdas.
class SimpleObjectAccess extends SimpleReadAccess implements ObjectAccess {

   SimpleObjectAccess(Object key) {
      super(key);
   }

   @Override
   public void reserve(Session session) {
      ((SessionImpl) session).reserveObjectVar(key);
   }

   @Override
   public void setObject(Session session, Object value) {
      SessionImpl impl = (SessionImpl) session;
      if (trace) {
         log.trace("#{} {} <- {}", impl.uniqueId(), key, Util.prettyPrintObject(value));
      }
      ObjectVar var = impl.getVar(key);
      var.value = value;
      var.set = true;
   }

   @Override
   public Object activate(Session session) {
      SessionImpl impl = (SessionImpl) session;
      ObjectVar var = impl.getVar(key);
      var.set = true;
      return var.get();
   }

   @Override
   public void unset(Session session) {
      SessionImpl impl = (SessionImpl) session;
      impl.getVar(key).unset();
   }
}
