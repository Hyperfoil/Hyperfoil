package io.hyperfoil.api.config;

import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.session.SequenceInstance;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.function.SerializableSupplier;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

class SequenceImpl implements Sequence {
   private static final Logger log = LoggerFactory.getLogger(SequenceImpl.class);

   private final SerializableSupplier<Phase> phase;
   private final String name;
   private final int id;
   private final int concurrency;
   private final Step[] steps;

   public SequenceImpl(SerializableSupplier<Phase> phase, String name, int id, int concurrency, Step[] steps) {
      this.phase = phase;
      this.name = name;
      this.id = id;
      this.concurrency = concurrency;
      this.steps = steps;
   }

   @Override
   public int id() {
      return id;
   }

   @Override
   public int concurrency() {
      return concurrency;
   }

   @Override
   public void instantiate(Session session, int index) {
      SequenceInstance instance = session.acquireSequence();
      if (instance == null) {
         log.error("Cannot instantiate sequence {}({}), no free instances.", name, id);
         session.fail(new IllegalStateException("No free sequence instances"));
      } else {
         instance.reset(name, id, index, steps);
         session.enableSequence(instance);
      }
   }

   @Override
   public void reserve(Session session) {
      ResourceUtilizer.reserve(session, (Object[]) steps);
   }

   @Override
   public String name() {
      return name;
   }


   @Override
   public Phase phase() {
      return phase.get();
   }

   @Override
   public Step[] steps() {
      return steps;
   }
}
