package io.hyperfoil.core.handlers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.hyperfoil.api.processor.Processor;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.session.Session;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;

public class DefragProcessor extends Processor.BaseDelegating
      implements ResourceUtilizer, Session.ResourceKey<DefragProcessor.Context> {
   private static final Logger log = LogManager.getLogger(DefragProcessor.class);

   public static Processor of(Processor delegate, boolean fragmented) {
      return fragmented ? new DefragProcessor(delegate) : delegate;
   }

   public DefragProcessor(Processor delegate) {
      super(delegate);
   }

   @Override
   public void process(Session session, ByteBuf data, int offset, int length, boolean isLastPart) {
      Context ctx = session.getResource(this);
      if (isLastPart && !ctx.isBuffering()) {
         delegate.process(session, data, offset, length, true);
         return;
      }
      if (data.isReadable()) {
         ctx.buffer(data, offset, length);
      }
      if (isLastPart) {
         ctx.flush(session, delegate);
      }
   }

   @Override
   public void reserve(Session session) {
      // Note: contrary to the recommended pattern the Context won't reserve all objects ahead, the CompositeByteBuf
      // will be allocated only if needed (and only once). This is necessary since we don't know the type of allocator
      // that is used for the received buffers ahead.
      session.declareResource(this, Context::new);
   }

   static class Context implements Session.Resource {
      CompositeByteBuf composite = null;

      boolean isBuffering() {
         return composite != null && composite.isReadable();
      }

      public void buffer(ByteBuf data, int offset, int length) {
         log.debug("Buffering {} bytes", length);
         if (composite == null) {
            composite = new CompositeByteBuf(data.alloc(), data.isDirect(), 16);
         }
         composite.addComponent(true, data.retainedSlice(offset, length));
      }

      void flush(Session session, Processor processor) {
         log.debug("Flushing {} bytes", composite.writerIndex());
         processor.process(session, composite, 0, composite.writerIndex(), true);
         // Note that processors generally don't modify readerIndex in the ByteBuf
         // so we cannot expect `data.isReadable() == false` at this point.
         composite.readerIndex(composite.writerIndex());
         composite.discardReadComponents();
      }

      @Override
      public void destroy() {
         if (composite != null) {
            composite.release();
         }
      }
   }
}
