package io.hyperfoil.api.session;

import java.util.ArrayList;

import io.hyperfoil.impl.CollectingVisitor;

public interface ResourceUtilizer {
   void reserve(Session session);

   static void reserveForTesting(Session session, Object o) {
      Visitor visitor = new Visitor();
      visitor.visit(o);
      for (ResourceUtilizer ru : visitor.resourceUtilizers()) {
         ru.reserve(session);
      }
   }

   class Visitor extends CollectingVisitor<ResourceUtilizer> {
      private final ArrayList<ResourceUtilizer> resourceUtilizers = new ArrayList<>();

      public Visitor() {
         super(ResourceUtilizer.class);
      }

      @Override
      protected boolean process(ResourceUtilizer value) {
         resourceUtilizers.add(value);
         return true;
      }

      public ResourceUtilizer[] resourceUtilizers() {
         return resourceUtilizers.toArray(new ResourceUtilizer[0]);
      }
   }
}
