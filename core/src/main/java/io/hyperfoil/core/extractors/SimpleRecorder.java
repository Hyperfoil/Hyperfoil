package io.hyperfoil.core.extractors;

import java.nio.charset.StandardCharsets;

import io.hyperfoil.api.connection.Request;
import io.hyperfoil.api.http.Processor;
import io.netty.buffer.ByteBuf;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.api.ResourceUtilizer;

public class SimpleRecorder implements Processor<Request>, ResourceUtilizer {
   private final String var;

   public SimpleRecorder(String var) {
      this.var = var;
   }

   @Override
   public void process(Request request, ByteBuf data, int offset, int length, boolean isLastPart) {
      assert isLastPart;
      request.session.setObject(var, data.toString(offset, length, StandardCharsets.UTF_8));
   }

   @Override
   public void reserve(Session session) {
      session.declare(var);
   }
}
