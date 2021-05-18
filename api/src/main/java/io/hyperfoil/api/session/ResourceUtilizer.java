package io.hyperfoil.api.session;

import io.hyperfoil.impl.ResourceVisitor;

public interface ResourceUtilizer {
   void reserve(Session session);

   static void reserveForTesting(Session session, Object o) {
      ResourceVisitor visitor = new ResourceVisitor();
      visitor.visit(null, o, null);
      for (ResourceUtilizer ru : visitor.resourceUtilizers()) {
         ru.reserve(session);
      }
   }
}
