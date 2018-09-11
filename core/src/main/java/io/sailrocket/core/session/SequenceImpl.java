package io.sailrocket.core.session;

import io.sailrocket.api.Sequence;
import io.sailrocket.api.SequenceInstance;
import io.sailrocket.api.Session;
import io.sailrocket.api.Step;
import io.sailrocket.core.api.ResourceUtilizer;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class SequenceImpl implements Sequence {
   private static final Logger log = LoggerFactory.getLogger(SequenceImpl.class);

   private final String name;
   private final int id;
   private final Step[] steps;

   public SequenceImpl(String name, int id, Step[] steps) {
      this.name = name;
      this.id = id;
      this.steps = steps;
   }

   @Override
   public void instantiate(Session session, int index) {
      SessionImpl impl = (SessionImpl) session;
      SequenceInstance instance = impl.acquireSequence();
      if (instance == null) {
         log.warn("Cannot instantiate sequence {} ({}), no free instances.", name, id);
      } else {
         instance.reset(name, id, index, steps);
         impl.enableSequence(instance);
      }
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
