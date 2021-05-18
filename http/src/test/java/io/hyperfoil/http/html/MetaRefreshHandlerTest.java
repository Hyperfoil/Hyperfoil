package io.hyperfoil.http.html;

import org.junit.Test;

import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.handlers.ExpectProcessor;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.core.util.Util;
import io.hyperfoil.http.HttpRunData;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

public class MetaRefreshHandlerTest {
   @Test
   public void test() {
      HtmlHandler.Builder html = new HtmlHandler.Builder();
      ExpectProcessor expect = new ExpectProcessor();
      html.handler(new HandlerBuilder(expect)).prepareBuild();
      HtmlHandler handler = html.build(true);
      Session session = SessionFactory.forTesting();
      HttpRunData.initForTesting(session);
      ResourceUtilizer.reserveForTesting(session, handler);
      handler.before(session);

      ByteBuf content1 = buf("content1");
      ByteBuf content2 = buf("content2");
      ByteBuf content5 = buf("content5");

      try {
         expect.expect(content1);
         sendChunk(handler, session, "<html><head><me");
         sendChunk(handler, session, "ta Http-equiv=\"refresh");
         sendChunk(handler, session, "\" content=\"");
         sendChunk(handler, session, "content1\" /><META");
         expect.expect(content2);
         sendChunk(handler, session, " content=\"content2\" foo=\"bar\" http-equiv=");
         sendChunk(handler, session, "\"Ref");
         sendChunk(handler, session, "resh\"></meta>");
         sendChunk(handler, session, "  <meta");
         sendChunk(handler, session, "META http-equiv=\"refresh\" content=\"content3\"/>");
         sendChunk(handler, session, "<!-- --><mETA http-equiv=\"whatever\" content=\"content4\" />");
         sendChunk(handler, session, "<mETA http-equiv=\"whatever\" content=\"content4\" />");
         expect.expect(content5);
         sendChunk(handler, session, "<meta content=\"content5\" http-equiv=\"refresh\" /></head></html>");
         handler.process(session, ByteBufAllocator.DEFAULT.buffer(0, 0), 0, 0, true);

         handler.after(session);
         expect.validate();
      } finally {
         content1.release();
         content2.release();
         content5.release();
      }
   }

   protected void sendChunk(HtmlHandler handler, Session session, String string) {
      ByteBuf buf = buf(string);
      handler.process(session, buf, buf.readerIndex(), buf.readableBytes(), false);
      buf.release();
   }

   private ByteBuf buf(String string) {
      return Util.string2byteBuf(string, ByteBufAllocator.DEFAULT.buffer());
   }

   // We cannot use lambda because of generics...
   private static class HandlerBuilder implements HtmlHandler.TagHandlerBuilder<HandlerBuilder> {
      private final ExpectProcessor expect;

      public HandlerBuilder(ExpectProcessor expect) {
         this.expect = expect;
      }

      @Override
      public HtmlHandler.TagHandler build() {
         return new MetaRefreshHandler(expect);
      }
   }
}
