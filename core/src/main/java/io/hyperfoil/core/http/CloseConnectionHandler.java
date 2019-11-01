package io.hyperfoil.core.http;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.processor.Processor;
import io.hyperfoil.api.connection.Request;
import io.hyperfoil.api.processor.RequestProcessorBuilder;
import io.netty.buffer.ByteBuf;

public class CloseConnectionHandler implements Processor<Request> {
   @Override
   public void process(Request request, ByteBuf data, int offset, int length, boolean isLastPart) {
      // ignored
   }

   @Override
   public void after(Request request) {
      request.connection().close();
   }

   @MetaInfServices(RequestProcessorBuilder.class)
   @Name("closeConnection")
   public static class Builder implements RequestProcessorBuilder {
      @Override
      public Processor<Request> build() {
         return new CloseConnectionHandler();
      }
   }
}
