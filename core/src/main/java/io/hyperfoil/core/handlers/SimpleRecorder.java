package io.hyperfoil.core.handlers;

import java.nio.charset.StandardCharsets;

import io.hyperfoil.api.connection.Request;
import io.hyperfoil.api.connection.Processor;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.core.session.SessionFactory;
import io.netty.buffer.ByteBuf;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.session.ResourceUtilizer;

public class SimpleRecorder implements Processor<Request>, ResourceUtilizer {
   private final Access toVar;

   public SimpleRecorder(String toVar) {
      this.toVar = SessionFactory.access(toVar);
   }

   @Override
   public void process(Request request, ByteBuf data, int offset, int length, boolean isLastPart) {
      assert isLastPart;
      toVar.setObject(request.session, data.toString(offset, length, StandardCharsets.UTF_8));
   }

   @Override
   public void reserve(Session session) {
      toVar.declareObject(session);
   }
}
