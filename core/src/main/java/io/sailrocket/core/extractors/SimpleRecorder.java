package io.sailrocket.core.extractors;

import java.nio.charset.StandardCharsets;

import io.netty.buffer.ByteBuf;
import io.sailrocket.api.Session;
import io.sailrocket.core.api.ResourceUtilizer;

public class SimpleRecorder implements Session.Processor, ResourceUtilizer {
   private final String var;

   public SimpleRecorder(String var) {
      this.var = var;
   }

   @Override
   public void process(Session session, ByteBuf data, int offset, int length, boolean isLastPart) {
      assert isLastPart;
      session.setObject(var, data.toString(offset, length, StandardCharsets.UTF_8));
   }

   @Override
   public void reserve(Session session) {
      session.declare(var);
   }
}
