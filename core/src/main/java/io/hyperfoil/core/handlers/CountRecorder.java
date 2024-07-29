package io.hyperfoil.core.handlers;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.InitFromParam;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.processor.Processor;
import io.hyperfoil.api.session.IntAccess;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.session.SessionFactory;
import io.netty.buffer.ByteBuf;

public class CountRecorder implements Processor {
   private final IntAccess toVar;

   public CountRecorder(IntAccess toVar) {
      this.toVar = toVar;
   }

   @Override
   public void before(Session session) {
      toVar.setInt(session, 0);
   }

   @Override
   public void process(Session session, ByteBuf data, int offset, int length, boolean isLastPart) {
      if (isLastPart) {
         toVar.addToInt(session, 1);
      }
   }

   /**
    * Records number of parts this processor is invoked on.
    */
   @MetaInfServices(Processor.Builder.class)
   @Name("count")
   public static class Builder implements Processor.Builder, InitFromParam<Builder> {
      private String toVar;

      /**
       * @param param Name of variable for the number of occurrences.
       * @return Self.
       */
      @Override
      public Builder init(String param) {
         return toVar(param);
      }

      /**
       * Variable where to store number of occurrences.
       *
       * @param toVar Variable name.
       * @return Self.
       */
      public Builder toVar(String toVar) {
         this.toVar = toVar;
         return this;
      }

      @Override
      public Processor build(boolean fragmented) {
         return new CountRecorder(SessionFactory.intAccess(toVar));
      }
   }
}
