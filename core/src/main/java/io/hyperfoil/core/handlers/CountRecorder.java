package io.hyperfoil.core.handlers;

import io.hyperfoil.api.connection.Request;
import io.hyperfoil.api.http.Processor;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.core.session.SessionFactory;
import io.netty.buffer.ByteBuf;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.session.ResourceUtilizer;

public class CountRecorder implements Processor<Request>, ResourceUtilizer {
   private final Access var;

   public CountRecorder(String var) {
      this.var = SessionFactory.access(var);
   }

   @Override
   public void before(Request request) {
      var.setInt(request.session, 0);
   }

   @Override
   public void process(Request request, ByteBuf data, int offset, int length, boolean isLastPart) {
      if (isLastPart) {
         var.addToInt(request.session, 1);
      }
   }

   @Override
   public void reserve(Session session) {
      var.declareInt(session);
   }
}