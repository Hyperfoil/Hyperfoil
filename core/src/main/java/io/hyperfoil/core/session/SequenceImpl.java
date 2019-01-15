package io.hyperfoil.core.session;

import io.hyperfoil.api.config.Sequence;
import io.hyperfoil.api.session.SequenceInstance;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.core.api.ResourceUtilizer;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class SequenceImpl implements Sequence {
   private static final Logger log = LoggerFactory.getLogger(SequenceImpl.class);

   private final String phase;
   private final String name;
   private final int id;
   private final Step[] steps;

   public SequenceImpl(String phase, String name, int id, Step[] steps) {
      this.phase = phase;
      this.name = name;
      this.id = id;
      this.steps = steps;
   }

   @Override
   public int id() {
      return id;
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

   @Override
   public String phase() {
      return phase;
   }

   @Override
   public Step[] steps() {
      return steps;
   }
}
