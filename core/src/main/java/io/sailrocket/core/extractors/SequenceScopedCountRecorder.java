package io.sailrocket.core.extractors;

import io.netty.buffer.ByteBuf;
import io.sailrocket.api.Session;
import io.sailrocket.core.machine.IntVar;
import io.sailrocket.core.machine.ResourceUtilizer;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class SequenceScopedCountRecorder implements Session.Processor, ResourceUtilizer {
   private static final Logger log = LoggerFactory.getLogger(SequenceScopedCountRecorder.class);
   private final String arrayVar;
   private final int numCounters;

   public SequenceScopedCountRecorder(String arrayVar, int numCounters) {
      this.arrayVar = arrayVar;
      this.numCounters = numCounters;
   }

   @Override
   public void before(Session session) {
      int index = getIndex((io.sailrocket.core.machine.Session) session);
      IntVar[] array = (IntVar[]) session.activate(arrayVar);
      array[index].set(0);
   }

   @Override
   public void process(Session session, ByteBuf buf, int offset, int length, boolean isLastPart) {
      if (isLastPart) {
         int index = getIndex((io.sailrocket.core.machine.Session) session);
         IntVar[] array = (IntVar[]) session.getObject(arrayVar);
         array[index].add(1);
      }
   }

   private int getIndex(io.sailrocket.core.machine.Session session) {
      int index = session.currentSequence().index();
      if (index < 0 || index >= numCounters) {
         log.warn("Index in {} out of bounds, {} has max {} counters", index, arrayVar, numCounters);
      }
      return index;
   }

   @Override
   public void reserve(io.sailrocket.core.machine.Session session) {
      session.declare(arrayVar);
      session.setObject(arrayVar, IntVar.newArray(session, numCounters));
      session.deactivate(arrayVar);
   }
}
