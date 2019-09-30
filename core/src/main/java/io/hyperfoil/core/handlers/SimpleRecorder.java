package io.hyperfoil.core.handlers;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.InitFromParam;
import io.hyperfoil.api.config.Name;
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

   /**
    * Stores data in a session variable (overwriting on multiple occurences).
    */
   @MetaInfServices(Request.ProcessorBuilder.class)
   @Name("simple")
   public static class Builder implements Request.ProcessorBuilder, InitFromParam<Builder> {
      private String toVar;
      private DataFormat format = DataFormat.STRING;

      @Override
      public Builder init(String param) {
         this.toVar = param;
         return this;
      }

      /**
       * Variable name.
       *
       * @param toVar Variable name.
       * @return Self.
       */
      public Builder toVar(String toVar) {
         this.toVar = toVar;
         return this;
      }

      /**
       * Format into which should this processor convert the buffers before storing. Default is <code>STRING</code>.
       *
       * @param format Data format.
       * @return Self.
       */
      public Builder format(DataFormat format) {
         this.format = format;
         return this;
      }

      @Override
      public Processor<Request> build() {
         return new SimpleRecorder(toVar, format);
      }
   }
}
