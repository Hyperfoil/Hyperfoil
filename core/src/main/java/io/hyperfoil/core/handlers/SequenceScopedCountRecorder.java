package io.hyperfoil.core.handlers;

import io.hyperfoil.api.connection.Request;
import io.hyperfoil.api.connection.Processor;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.core.session.SessionFactory;
import io.netty.buffer.ByteBuf;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.session.IntVar;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class SequenceScopedCountRecorder implements Processor<Request>, ResourceUtilizer {
   private static final Logger log = LoggerFactory.getLogger(SequenceScopedCountRecorder.class);
   private final Access arrayVar;
   private final int numCounters;

   public SequenceScopedCountRecorder(String arrayVar, int numCounters) {
      this.arrayVar = SessionFactory.access(arrayVar);
      this.numCounters = numCounters;
   }

   @Override
   public void before(Request request) {
      int index = getIndex(request.session);
      IntVar[] array = (IntVar[]) arrayVar.activate(request.session);
      array[index].set(0);
   }

   @Override
   public void process(Request request, ByteBuf buf, int offset, int length, boolean isLastPart) {
      if (isLastPart) {
         int index = getIndex(request.session);
         IntVar[] array = (IntVar[]) arrayVar.getObject(request.session);
         array[index].add(1);
      }
   }

   private int getIndex(Session session) {
      int index = session.currentSequence().index();
      if (index < 0 || index >= numCounters) {
         log.warn("Index in {} out of bounds, {} has max {} counters", index, arrayVar, numCounters);
      }
      return index;
   }

   @Override
   public void reserve(Session session) {
      arrayVar.declareObject(session);
      arrayVar.setObject(session, IntVar.newArray(session, numCounters));
      arrayVar.unset(session);
   }
}
