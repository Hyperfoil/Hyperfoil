package io.hyperfoil.api.session;

public interface ResourceUtilizer {
   void reserve(Session session);

   static void reserve(Session session, Object... objects) {
      for (Object o : objects) {
         if (o instanceof ResourceUtilizer) {
            ((ResourceUtilizer) o).reserve(session);
         }
      }
   }
}
