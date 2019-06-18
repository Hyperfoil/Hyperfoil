package io.hyperfoil.core.handlers;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.Locator;
import io.hyperfoil.api.connection.Request;
import io.hyperfoil.api.connection.Processor;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.core.data.DataFormat;
import io.hyperfoil.core.session.SessionFactory;
import io.netty.buffer.ByteBuf;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.session.ResourceUtilizer;

public class SimpleRecorder implements Processor<Request>, ResourceUtilizer {
   private final Access toVar;
   private final DataFormat format;

   public SimpleRecorder(String toVar, DataFormat format) {
      this.toVar = SessionFactory.access(toVar);
      this.format = format;
   }

   @Override
   public void process(Request request, ByteBuf data, int offset, int length, boolean isLastPart) {
      assert isLastPart;
      Object value = format.convert(data, offset, length);
      toVar.setObject(request.session, value);
   }

   @Override
   public void reserve(Session session) {
      toVar.declareObject(session);
   }

   public static class Builder implements Processor.Builder<Request> {
      private String toVar;
      private DataFormat format = DataFormat.STRING;

      public Builder(String toVar) {
         this.toVar = toVar;
      }

      public Builder toVar(String toVar) {
         this.toVar = toVar;
         return this;
      }

      public Builder format(DataFormat format) {
         this.format = format;
         return this;
      }

      @Override
      public Processor<Request> build() {
         return new SimpleRecorder(toVar, format);
      }
   }

   @MetaInfServices(Request.ProcessorBuilderFactory.class)
   public static class BuilderFactory implements Request.ProcessorBuilderFactory {
      @Override
      public String name() {
         return "simpleRecorder";
      }

      @Override
      public boolean acceptsParam() {
         return true;
      }

      @Override
      public Builder newBuilder(Locator locator, String param) {
         return new Builder(param);
      }
   }
}
