package io.hyperfoil.core.session;

import io.hyperfoil.api.session.Session;

public class SimpleReadAccess extends BaseAccess {
   public SimpleReadAccess(Object key) {
      super(key);
   }

   @Override
   public boolean isSet(Session session) {
      SessionImpl impl = (SessionImpl) session;
      return impl.getVar(index).isSet();
   }

   @Override
   public Object getObject(Session session) {
      SessionImpl impl = (SessionImpl) session;
      return impl.requireSet(index, key).objectValue(session);
   }

   @Override
   public int getInt(Session session) {
      SessionImpl impl = (SessionImpl) session;
      IntVar var = impl.requireSet(index, key);
      return var.intValue(impl);
   }

   @Override
   public Session.Var getVar(Session session) {
      SessionImpl impl = (SessionImpl) session;
      return impl.getVar(index);
   }

   @Override
   public boolean isSequenceScoped() {
      return false;
   }

   @Override
   public String toString() {
      return key.toString();
   }
}
