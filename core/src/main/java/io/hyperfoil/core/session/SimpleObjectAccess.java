package io.hyperfoil.core.session;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.session.ObjectAccess;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.util.Util;

class SimpleObjectAccess extends SimpleReadAccess implements ObjectAccess {

   SimpleObjectAccess(Object key) {
      super(key);
   }

   @Override
   public Session.Var createVar(Session session, Session.Var existing) {
      if (existing == null) {
         return new ObjectVar((SessionImpl) session);
      } else if (existing instanceof ObjectVar) {
         return existing;
      } else {
         throw new BenchmarkDefinitionException("Variable " + key + " should hold an object but it is defined to hold an integer elsewhere.");
      }
   }

   @Override
   public void setObject(Session session, Object value) {
      SessionImpl impl = (SessionImpl) session;
      if (trace) {
         log.trace("#{} {} <- {}", impl.uniqueId(), key, Util.prettyPrintObject(value));
      }
      ObjectVar var = impl.getVar(index);
      var.value = value;
      var.set = true;
   }

   @Override
   public Object activate(Session session) {
      SessionImpl impl = (SessionImpl) session;
      ObjectVar var = impl.getVar(index);
      var.set = true;
      return var.objectValue(session);
   }

   @Override
   public void unset(Session session) {
      SessionImpl impl = (SessionImpl) session;
      impl.getVar(index).unset();
   }
}
