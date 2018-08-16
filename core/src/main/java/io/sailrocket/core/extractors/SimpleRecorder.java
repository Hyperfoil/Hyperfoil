package io.sailrocket.core.extractors;

import java.nio.charset.Charset;

import io.netty.buffer.ByteBuf;
import io.sailrocket.api.Session;
import io.sailrocket.core.machine.ResourceUtilizer;

public class SimpleRecorder implements Session.Processor, ResourceUtilizer {
   private final String var;

   public SimpleRecorder(String var) {
      this.var = var;
   }

   @Override
   public void process(Session session, ByteBuf data, int offset, int length) {
      session.setObject(var, data.toString(offset, length, Charset.forName("UTF-8")));
   }

   @Override
   public void reserve(io.sailrocket.core.machine.Session session) {
      session.declare(var);
   }
}
