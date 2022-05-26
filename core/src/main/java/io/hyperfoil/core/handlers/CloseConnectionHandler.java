package io.hyperfoil.core.handlers;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.processor.Processor;
import io.hyperfoil.api.session.Session;
import io.netty.buffer.ByteBuf;

public class CloseConnectionHandler implements Processor {
   @Override
   public void process(Session session, ByteBuf data, int offset, int length, boolean isLastPart) {
      // ignored
   }

   @Override
   public void after(Session session) {
      session.currentRequest().connection().close();
   }

   /**
    * Prevents reuse connection after the response has been handled.
    */
   @MetaInfServices(Processor.Builder.class)
   @Name("closeConnection")
   public static class Builder implements Processor.Builder {
      @Override
      public Processor build(boolean fragmented) {
         return new CloseConnectionHandler();
      }
   }
}
