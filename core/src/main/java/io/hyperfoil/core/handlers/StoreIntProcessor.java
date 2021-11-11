package io.hyperfoil.core.handlers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.InitFromParam;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.processor.Processor;
import io.hyperfoil.api.session.IntAccess;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.impl.Util;
import io.netty.buffer.ByteBuf;

public class StoreIntProcessor implements Processor {
   private static final Logger log = LogManager.getLogger(StoreIntProcessor.class);

   private final IntAccess toVar;
   private final boolean override;

   public StoreIntProcessor(IntAccess toVar, boolean override) {
      this.toVar = toVar;
      this.override = override;
   }

   @Override
   public void process(Session session, ByteBuf data, int offset, int length, boolean isLastPart) {
      ensureDefragmented(isLastPart);
      if (toVar.isSet(session) && !override) {
         log.warn("#{} Variable {} was already set, not setting again.", session.uniqueId(), toVar);
      } else {
         try {
            long value = Util.parseLong(data, offset, length);
            toVar.setInt(session, (int) value);
         } catch (NumberFormatException e) {
            log.warn("#{} Not storing anything because it cannot be parsed to integer: {}", session.uniqueId(), Util.toString(data, offset, length));
         }
      }
   }

   /**
    * Converts buffers into integral value and stores it in a variable.
    */
   @MetaInfServices(Processor.Builder.class)
   @Name("storeInt")
   public static class Builder implements Processor.Builder, InitFromParam<Builder> {
      private String toVar;
      private boolean override;

      /**
       * Name of variable where to store the integral value.
       *
       * @param param Name of integer variable where to store the value.
       * @return Self.
       */
      @Override
      public Builder init(String param) {
         return toVar(param);
      }

      /**
       * Name of variable where to store the integral value.
       *
       * @param toVar Variable name.
       * @return Self.
       */
      public Builder toVar(String toVar) {
         this.toVar = toVar;
         return this;
      }

      /**
       * Allow the value to be set multiple times (last write wins). Defaults to false.
       *
       * @param override Allow override.
       * @return Self.
       */
      public Builder override(boolean override) {
         this.override = override;
         return this;
      }

      @Override
      public Processor build(boolean fragmented) {
         return DefragProcessor.of(new StoreIntProcessor(SessionFactory.intAccess(toVar), override), fragmented);
      }
   }
}
