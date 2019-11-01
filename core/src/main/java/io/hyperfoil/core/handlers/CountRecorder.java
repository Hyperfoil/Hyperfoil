package io.hyperfoil.core.handlers;

import io.hyperfoil.api.connection.Request;
import io.hyperfoil.api.processor.Processor;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.core.session.SessionFactory;
import io.netty.buffer.ByteBuf;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.session.ResourceUtilizer;

public class CountRecorder implements Processor<Request>, ResourceUtilizer {
   private final Access toVar;

   public CountRecorder(String toVar) {
      this.toVar = SessionFactory.access(toVar);
   }

   @Override
   public void before(Request request) {
      toVar.setInt(request.session, 0);
   }

   @Override
   public void process(Request request, ByteBuf data, int offset, int length, boolean isLastPart) {
      if (isLastPart) {
         toVar.addToInt(request.session, 1);
      }
   }

   @Override
   public void reserve(Session session) {
      toVar.declareInt(session);
   }
}