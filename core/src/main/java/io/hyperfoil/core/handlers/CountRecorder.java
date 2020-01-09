package io.hyperfoil.core.handlers;

import io.hyperfoil.api.processor.Processor;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.core.session.SessionFactory;
import io.netty.buffer.ByteBuf;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.session.ResourceUtilizer;

public class CountRecorder implements Processor, ResourceUtilizer {
   private final Access toVar;

   public CountRecorder(String toVar) {
      this.toVar = SessionFactory.access(toVar);
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

   @Override
   public void reserve(Session session) {
      toVar.declareInt(session);
   }
}