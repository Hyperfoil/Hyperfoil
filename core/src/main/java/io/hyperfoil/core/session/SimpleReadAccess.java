package io.hyperfoil.core.session;

import java.util.Objects;

import io.hyperfoil.api.session.ReadAccess;
import io.hyperfoil.api.session.Session;

public class SimpleReadAccess implements ReadAccess {
   protected final Object key;

   public SimpleReadAccess(Object key) {
      this.key = Objects.requireNonNull(key);
   }

   @Override
   public boolean isSet(Session session) {
      SessionImpl impl = (SessionImpl) session;
      return impl.getVar(key).isSet();
   }

   @Override
   public Object getObject(Session session) {
      SessionImpl impl = (SessionImpl) session;
      return impl.getObject(key);
   }

   @Override
   public int getInt(Session session) {
      SessionImpl impl = (SessionImpl) session;
      return impl.getInt(key);
   }

   @Override
   public Session.Var getVar(Session session) {
      SessionImpl impl = (SessionImpl) session;
      return impl.getVar(key);
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
