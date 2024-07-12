package io.hyperfoil.core.handlers;

import io.hyperfoil.api.processor.Transformer;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.session.Session;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class DefragTransformer extends Transformer.BaseDelegating implements ResourceUtilizer, Session.ResourceKey<DefragTransformer.Context> {
   private static final Logger log = LogManager.getLogger(DefragTransformer.class);

   public DefragTransformer(Transformer delegate) {
      super(delegate);
   }

   @Override
   public void transform(Session session, ByteBuf in, int offset, int length, boolean lastFragment, ByteBuf out) {
      Context ctx = session.getResource(this);
      if (lastFragment && !ctx.isBuffering()) {
         delegate.transform(session, in, offset, length, true, out);
         return;
      }
      if (in.isReadable()) {
         ctx.buffer(in, offset, length);
      }
      if (lastFragment) {
         ctx.flush(session, delegate, out);
      }
   }

   @Override
   public void reserve(Session session) {
      // Note: contrary to the recommended pattern the Context won't reserve all objects ahead, the CompositeByteBuf
      // will be allocated only if needed (and only once). This is necessary since we don't know the type of allocator
      // that is used for the received buffers ahead.
      session.declareResources().add(this, Context::new);
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

      void flush(Session session, Transformer transformer, ByteBuf out) {
         log.debug("Flushing {} bytes", composite.writerIndex());
         transformer.transform(session, composite, 0, composite.writerIndex(), true, out);
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
