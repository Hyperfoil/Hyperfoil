package io.hyperfoil.core.handlers;

import io.hyperfoil.api.connection.Request;
import io.hyperfoil.api.http.Processor;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class DefragProcessor<R extends Request> extends Processor.BaseDelegating<R> implements ResourceUtilizer, Session.ResourceKey<DefragProcessor.Context> {
   private static final Logger log = LoggerFactory.getLogger(DefragProcessor.class);

   public DefragProcessor(Processor<R> delegate) {
      super(delegate);
   }

   @Override
   public void before(R request) {
      delegate.before(request);
   }

   @Override
   public void process(R request, ByteBuf data, int offset, int length, boolean isLastPart) {
      Context<R> ctx = request.session.getResource(this);
      if (isLastPart && !ctx.isBuffering()) {
         delegate.process(request, data, offset, length, true);
         return;
      }
      ctx.buffer(data, offset, length);
      if (isLastPart) {
         ctx.flush(request, delegate);
      }
   }

   @Override
   public void after(R request) {
      delegate.after(request);
   }

   @Override
   public void reserve(Session session) {
      session.declare(this);
      // Note: contrary to the recommended pattern the Context won't reserve all objects ahead, the CompositeByteBuf
      // will be allocated only if needed (and only once). This is necessary since we don't know the type of allocator
      // that is used for the received buffers ahead.
      session.declareResource(this, new Context());
      if (delegate instanceof ResourceUtilizer) {
         ((ResourceUtilizer) delegate).reserve(session);
      }
   }

   static class Context<R extends Request> implements Session.Resource {
      CompositeByteBuf composite = null;

      public boolean isBuffering() {
         return composite != null && composite.isReadable();
      }

      public void buffer(ByteBuf data, int offset, int length) {
         log.debug("Buffering {} bytes", length);
         if (composite == null) {
            composite = new CompositeByteBuf(data.alloc(), data.isDirect(), 16);
         }
         composite.addComponent(true, data.retainedSlice(offset, length));
      }

      public void flush(R request, Processor<R> processor) {
         log.debug("Flushing {} bytes", composite.writerIndex());
         processor.process(request, composite, 0, composite.writerIndex(), true);
         // Not sure if this is the right call to forget/reuse the components (if needed)
         composite.clear();
      }
   }
}
