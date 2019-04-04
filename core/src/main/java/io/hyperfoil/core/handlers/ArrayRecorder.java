package io.hyperfoil.core.handlers;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.Locator;
import io.hyperfoil.api.connection.Request;
import io.hyperfoil.api.http.Processor;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.core.data.DataFormat;
import io.hyperfoil.core.session.SessionFactory;
import io.netty.buffer.ByteBuf;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.session.ObjectVar;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class ArrayRecorder implements Processor<Request>, ResourceUtilizer {
   private static final Logger log = LoggerFactory.getLogger(ArrayRecorder.class);
   private final Access var;
   private final DataFormat format;
   private final int maxSize;

   public ArrayRecorder(String var, DataFormat format, int maxSize) {
      this.var = SessionFactory.access(var);
      this.format = format;
      this.maxSize = maxSize;
   }

   public void before(Request request) {
      ObjectVar[] array = (ObjectVar[]) var.activate(request.session);
      for (int i = 0; i < array.length; ++i) {
         array[i].unset();
      }
   }

   @Override
   public void process(Request request, ByteBuf data, int offset, int length, boolean isLastPart) {
      assert isLastPart;
      ObjectVar[] array = (ObjectVar[]) var.activate(request.session);
      Object value = format.convert(data, offset, length);
      for (int i = 0; i < array.length; ++i) {
         if (array[i].isSet()) continue;
         array[i].set(value);
         return;
      }
      log.warn("Exceed maximum size of the array {} ({}), dropping value {}", var, maxSize, value);
   }

   @Override
   public void reserve(Session session) {
      var.declareObject(session);
      var.setObject(session, ObjectVar.newArray(session, maxSize));
      var.unset(session);
   }

   public static class Builder implements Processor.Builder<Request> {
      private String var;
      private DataFormat format = DataFormat.STRING;
      private int maxSize;

      @Override
      public ArrayRecorder build() {
         return new ArrayRecorder(var, format, maxSize);
      }

      public Builder var(String var) {
         this.var = var;
         return this;
      }

      public Builder maxSize(int maxSize) {
         this.maxSize = maxSize;
         return this;
      }

      public Builder format(DataFormat format) {
         this.format = format;
         return this;
      }
   }

   @MetaInfServices(Request.ProcessorBuilderFactory.class)
   public static class BuilderFactory implements Request.ProcessorBuilderFactory {
      @Override
      public String name() {
         return "array";
      }

      @Override
      public boolean acceptsParam() {
         return false;
      }

      @Override
      public Builder newBuilder(Locator locator, String param) {
         return new Builder();
      }
   }
}
