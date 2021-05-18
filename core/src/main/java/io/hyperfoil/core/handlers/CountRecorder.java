package io.hyperfoil.core.handlers;

import io.hyperfoil.api.processor.Processor;
import io.hyperfoil.api.session.IntAccess;
import io.netty.buffer.ByteBuf;
import io.hyperfoil.api.session.Session;

public class CountRecorder implements Processor {
   private final IntAccess toVar;

   public CountRecorder(IntAccess toVar) {
      this.toVar = toVar;
   }

   @Override
   public void before(Session session) {
      toVar.setInt(session, 0);
   }

   @Override
   public void process(Session session, ByteBuf data, int offset, int length, boolean isLastPart) {
      if (isLastPart) {
         toVar.addToInt(session, 1);
      }
   }
}