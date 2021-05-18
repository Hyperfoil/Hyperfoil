package io.hyperfoil.api.session;

import java.io.Serializable;

public interface ReadAccess extends Serializable {
   boolean isSet(Session session);

   Object getObject(Session session);

   int getInt(Session session);

   boolean isSequenceScoped();

   Session.Var getVar(Session session);

   Object key();

   void setIndex(int index);

   int index();
}
