package io.hyperfoil.core.extractors;

import io.hyperfoil.api.connection.Request;
import io.hyperfoil.api.http.Processor;
import io.netty.buffer.ByteBuf;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.session.ResourceUtilizer;

public class CountRecorder implements Processor<Request>, ResourceUtilizer {
   private final String var;

   public CountRecorder(String var) {
      this.var = var;
   }

   @Override
   public void before(Request request) {
      request.session.setInt(var, 0);
   }

   @Override
   public void process(Request request, ByteBuf data, int offset, int length, boolean isLastPart) {
      if (isLastPart) {
         request.session.addToInt(var, 1);
      }
   }

   @Override
   public void reserve(Session session) {
      session.declareInt(var);
   }
}