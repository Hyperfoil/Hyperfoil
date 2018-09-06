package io.sailrocket.core.session;

import io.sailrocket.api.Sequence;
import io.sailrocket.api.SequenceInstance;
import io.sailrocket.api.Session;
import io.sailrocket.api.Step;
import io.sailrocket.core.api.ResourceUtilizer;

public class SequenceImpl implements Sequence {
   // debug only
   private final String name;
   private int id;
   private final Step[] steps;

   public SequenceImpl(String name) {
      this(name, null);
   }

   public SequenceImpl(String name, Step[] steps) {
      this.name = name;
      this.steps = steps;
   }

   @Override
   public void id(int id) {
      this.id = id;
   }

   @Override
   public int id() {
      return id;
   }

   @Override
   public void instantiate(Session session, int index) {
      SessionImpl impl = (SessionImpl) session;
      SequenceInstance instance = impl.acquireSequence();
      instance.reset(name, id, index, steps);
      impl.enableSequence(instance);
   }

   @Override
   public void reserve(Session session) {
      for (Step a : steps) {
         if (a instanceof ResourceUtilizer) {
            ((ResourceUtilizer) a).reserve(session);
         }
      }
   }

   @Override
   public String name() {
      return name;
   }
}
