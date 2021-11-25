package io.hyperfoil.http.html;

import java.nio.charset.StandardCharsets;

import io.hyperfoil.api.processor.Processor;
import io.hyperfoil.api.session.Session;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

public class MetaRefreshHandler implements HtmlHandler.TagHandler {
   private static final byte[] META = "meta".getBytes(StandardCharsets.UTF_8);
   private static final byte[] HTTP_EQUIV = "http-equiv".getBytes(StandardCharsets.UTF_8);
   private static final byte[] REFRESH = "refresh".getBytes(StandardCharsets.UTF_8);
   private static final byte[] CONTENT = "content".getBytes(StandardCharsets.UTF_8);

   private final Processor processor;

   public MetaRefreshHandler(Processor processor) {
      this.processor = processor;
   }

   @Override
   public Processor processor() {
      return processor;
   }

   @Override
   public HtmlHandler.HandlerContext newContext() {
      return new Context();
   }

   class Context implements HtmlHandler.HandlerContext {
      private final Match meta = new Match();
      private final Match httpEquiv = new Match();
      private final Match content = new Match();
      private final Match refresh = new Match();
      private final ByteBuf valueBuffer = ByteBufAllocator.DEFAULT.buffer();

      @Override
      public void onTag(Session session, boolean close, ByteBuf data, int offset, int length, boolean isLast) {
         if (close) {
            endTag(session, true);
         } else {
            meta.shift(data, offset, length, isLast, META);
         }
      }

      @Override
      public void onAttr(Session session, ByteBuf data, int offset, int length, boolean isLast) {
         if (meta.hasMatch()) {
            httpEquiv.shift(data, offset, length, isLast, HTTP_EQUIV);
            content.shift(data, offset, length, isLast, CONTENT);
         }
      }

      @Override
      public void onValue(Session session, ByteBuf data, int offset, int length, boolean isLast) {
         if (httpEquiv.hasMatch()) {
            refresh.shift(data, offset, length, isLast, REFRESH);
            if (refresh.hasMatch() && valueBuffer.isReadable()) {
               processor.process(session, valueBuffer, valueBuffer.readerIndex(), valueBuffer.readableBytes(), true);
               valueBuffer.clear();
            }
         } else if (content.hasMatch()) {
            valueBuffer.writeBytes(data, offset, length);
            if (refresh.hasMatch() && isLast) {
               processor.process(session, valueBuffer, valueBuffer.readerIndex(), valueBuffer.readableBytes(), true);
               valueBuffer.clear();
            }
         }
      }

      @Override
      public void endTag(Session session, boolean closing) {
         meta.reset();
         httpEquiv.reset();
         content.reset();
         refresh.reset();
         valueBuffer.clear();
      }

      @Override
      public void destroy() {
         valueBuffer.release();
      }
   }

   public static class Builder implements HtmlHandler.TagHandlerBuilder<Builder> {
      private Processor.Builder processor;

      public Builder processor(Processor.Builder processor) {
         this.processor = processor;
         return this;
      }

      @Override
      public MetaRefreshHandler build() {
         return new MetaRefreshHandler(processor.build(false));
      }
   }
}
