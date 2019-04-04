package io.hyperfoil.core.session;

import java.util.Objects;

import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.Session;

// TODO: In the future it would be great if we could just hold an index into Session and do array access
// instead of map lookup by name. However that would require us to know all Access instances ahead;
// we cannot get them through .declareObject()/.declareInt() as this happens on a live session and Access
// objects are not bound to any session. Steps (and other scenario objects) could implement an interface
// to provide all Access objects but often we hold just functions and that would prohibit use of lambdas.
class SimpleAccess implements Access {
   private final Object key;

   SimpleAccess(Object key) {
      this.key = Objects.requireNonNull(key);
   }

   @Override
   public void declareObject(Session session) {
      SessionImpl impl = (SessionImpl) session;
      impl.declareObject(key);
   }

   @Override
   public void declareInt(Session session) {
      SessionImpl impl = (SessionImpl) session;
      impl.declareInt(key);
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
   public void setObject(Session session, Object value) {
      SessionImpl impl = (SessionImpl) session;
      impl.setObject(key, value);
   }

   @Override
   public int getInt(Session session) {
      SessionImpl impl = (SessionImpl) session;
      return impl.getInt(key);
   }

   @Override
   public void setInt(Session session, int value) {
      SessionImpl impl = (SessionImpl) session;
      impl.setInt(key, value);
   }

   @Override
   public <V extends Session.Var> V getVar(Session session) {
      SessionImpl impl = (SessionImpl) session;
      return impl.getVar(key);
   }

   @Override
   public int addToInt(Session session, int delta) {
      SessionImpl impl = (SessionImpl) session;
      return impl.addToInt(key, delta);
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

   @Override
   public String toString() {
      return key.toString();
   }
}
