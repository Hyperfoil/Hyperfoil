package io.hyperfoil.core.handlers;

import java.util.ArrayList;
import java.util.List;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.InitFromParam;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.processor.Processor;
import io.hyperfoil.api.session.ObjectAccess;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.data.DataFormat;
import io.hyperfoil.core.session.SessionFactory;
import io.netty.buffer.ByteBuf;

public class CollectionRecorder implements Processor {
   private final ObjectAccess toVar;
   private final DataFormat format;

   public CollectionRecorder(ObjectAccess toVar, DataFormat format) {
      this.toVar = toVar;
      this.format = format;
   }

   @Override
   public void process(Session session, ByteBuf data, int offset, int length, boolean isLastPart) {
      ensureDefragmented(isLastPart);
      List<Object> list;
      if (!toVar.isSet(session)) {
         list = new ArrayList<>();
         toVar.setObject(session, list);
      } else {
         Object obj = toVar.getObject(session);
         if (obj instanceof List) {
            //noinspection unchecked
            list = (List<Object>) obj;
         } else {
            list = new ArrayList<>();
            toVar.setObject(session, list);
         }
      }
      list.add(format.convert(data, offset, length));
   }

   /**
    * Collects results of processor invocation into a unbounded list.
    * WARNING: This processor should be used rarely as it allocates memory during the benchmark.
    */
   @MetaInfServices(Processor.Builder.class)
   @Name("collection")
   public static class Builder implements Processor.Builder, InitFromParam<Builder> {
      private String toVar;
      private DataFormat format = DataFormat.STRING;

      /**
       * @param param Variable name to store the list.
       * @return Self.
       */
      @Override
      public Builder init(String param) {
         return toVar(param);
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
      public Processor build(boolean fragmented) {
         return DefragProcessor.of(new CollectionRecorder(SessionFactory.objectAccess(toVar), format), fragmented);
      }
   }
}
