package io.hyperfoil.core.handlers;

import java.nio.charset.StandardCharsets;

import io.hyperfoil.api.connection.Request;
import io.hyperfoil.api.http.Processor;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.core.session.SessionFactory;
import io.netty.buffer.ByteBuf;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.session.ResourceUtilizer;

public class SimpleRecorder implements Processor<Request>, ResourceUtilizer {
   private final Access var;

   public SimpleRecorder(String var) {
      this.var = SessionFactory.access(var);
   }

   @Override
   public void process(Request request, ByteBuf data, int offset, int length, boolean isLastPart) {
      assert isLastPart;
      var.setObject(request.session, data.toString(offset, length, StandardCharsets.UTF_8));
   }

   @Override
   public void reserve(Session session) {
      var.declareObject(session);
   }
}
