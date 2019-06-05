package io.hyperfoil.api.session;

import java.util.Collection;

public interface ResourceUtilizer {
   void reserve(Session session);

   static void reserve(Session session, Object... objects) {
      if (objects == null) {
         return;
      }
      for (Object o : objects) {
         if (o instanceof ResourceUtilizer) {
            ((ResourceUtilizer) o).reserve(session);
         }
      }
   }

   static void reserve(Session session, Collection<?> objects) {
      for (Object o : objects) {
         if (o instanceof ResourceUtilizer) {
            ((ResourceUtilizer) o).reserve(session);
         }
      }
   }
}
