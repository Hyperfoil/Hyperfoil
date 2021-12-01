package io.hyperfoil.core.handlers;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.InitFromParam;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.processor.Processor;
import io.hyperfoil.api.session.ObjectAccess;
import io.hyperfoil.core.data.DataFormat;
import io.hyperfoil.core.session.SessionFactory;
import io.netty.buffer.ByteBuf;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.session.ObjectVar;
import io.hyperfoil.api.session.ResourceUtilizer;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class ArrayRecorder implements Processor, ResourceUtilizer {
   private static final Logger log = LogManager.getLogger(ArrayRecorder.class);
   private static final boolean trace = log.isTraceEnabled();
   private final ObjectAccess toVar;
   private final DataFormat format;
   private final int maxSize;
   private final boolean silent;

   public ArrayRecorder(ObjectAccess toVar, DataFormat format, int maxSize, boolean silent) {
      this.toVar = toVar;
      this.format = format;
      this.maxSize = maxSize;
      this.silent = silent;
   }

   public void before(Session session) {
      ObjectVar[] array = (ObjectVar[]) toVar.activate(session);
      for (int i = 0; i < array.length; ++i) {
         array[i].unset();
      }
   }

   @Override
   public void process(Session session, ByteBuf data, int offset, int length, boolean isLastPart) {
      ensureDefragmented(isLastPart);
      ObjectVar[] array = (ObjectVar[]) toVar.activate(session);
      Object value = format.convert(data, offset, length);
      for (int i = 0; i < array.length; ++i) {
         if (array[i].isSet()) continue;
         if (trace) {
            log.trace("#{} Set {}[{}] <- {}", session.uniqueId(), toVar.toString(), i, value);
         }
         array[i].set(value);
         return;
      }
      if (silent) {
         if (trace) {
            log.trace("Exceeded maximum size of the array {} ({}), dropping value {}", toVar, maxSize, value);
         }
      } else {
         log.warn("Exceeded maximum size of the array {} ({}), dropping value {}", toVar, maxSize, value);
      }
   }

   @Override
   public void reserve(Session session) {
      toVar.setObject(session, ObjectVar.newArray(session, maxSize));
      toVar.unset(session);
   }

   /**
    * Stores data in an array stored as session variable.
    */
   @MetaInfServices(Processor.Builder.class)
   @Name("array")
   public static class Builder implements Processor.Builder, InitFromParam<Builder> {
      private String toVar;
      private DataFormat format = DataFormat.STRING;
      private int maxSize;
      private boolean silent;

      /**
       * @param param Use format <code>toVar[maxSize]</code>.
       * @return Self.
       */
      @Override
      public Builder init(String param) {
         int b1 = param.indexOf('[');
         int b2 = param.indexOf(']');
         if (b1 < 0 || b2 < 0 || b2 - b1 < 1) {
            throw new BenchmarkDefinitionException("Array variable must have maximum size: use var[maxSize], e.g. 'foo[16]'");
         }
         try {
            maxSize = Integer.parseInt(param.substring(b1 + 1, b2));
         } catch (NumberFormatException e) {
            throw new BenchmarkDefinitionException("Cannot parse maximum size in '" + param + "'");
         }
         toVar = param.substring(0, b1).trim();
         return this;
      }

      @Override
      public Processor build(boolean fragmented) {
         return DefragProcessor.of(new ArrayRecorder(SessionFactory.objectAccess(toVar), format, maxSize, silent), fragmented);
      }

      /**
       * Variable name.
       *
       * @param var Variable name.
       * @return Self.
       */
      public Builder toVar(String var) {
         this.toVar = var;
         return this;
      }

      /**
       * Maximum size of the array.
       *
       * @param maxSize Max number of elements.
       * @return Self.
       */
      public Builder maxSize(int maxSize) {
         this.maxSize = maxSize;
         return this;
      }

      /**
       * Do not log warnings when the maximum size is exceeded.
       *
       * @param silent Boolean value.
       * @return Self.
       */
      public Builder silent(boolean silent) {
         this.silent = silent;
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
   }
}
