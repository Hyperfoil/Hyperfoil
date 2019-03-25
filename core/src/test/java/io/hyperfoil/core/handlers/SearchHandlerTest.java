package io.hyperfoil.core.handlers;

import java.nio.charset.StandardCharsets;

import org.junit.Test;

import io.hyperfoil.api.connection.HttpRequest;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.session.SessionFactory;

public class SearchHandlerTest {
   @Test
   public void testSimple() {
      ExpectProcessor processor = new ExpectProcessor();
      processor.expect(6, 3, true);
      SearchHandler handler = new SearchHandler("foo", "bar", processor);
      runHandler(handler, processor, "yyyfooxxxbaryyy");
   }

   @Test
   public void testStartEnd() {
      ExpectProcessor processor = new ExpectProcessor();
      processor.expect(3, 2, true);
      SearchHandler handler = new SearchHandler("foo", "bar", processor);
      runHandler(handler, processor, "fooxxbar");
   }

   @Test
   public void testEmpty() {
      ExpectProcessor processor = new ExpectProcessor();
      processor.expect(3, 0, true);
      SearchHandler handler = new SearchHandler("foo", "bar", processor);
      runHandler(handler, processor, "foobar");
   }

   @Test
   public void testNotEnding() {
      ExpectProcessor processor = new ExpectProcessor();
      SearchHandler handler = new SearchHandler("foo", "bar", processor);
      runHandler(handler, processor, "fooxxx");
   }

   @Test
   public void testGreedy() {
      ExpectProcessor processor = new ExpectProcessor();
      processor.expect(3, 6, true);
      SearchHandler handler = new SearchHandler("foo", "bar", processor);
      runHandler(handler, processor, "foofooxxxbar");
   }

   @Test
   public void testSplitMany() {
      ExpectProcessor processor = new ExpectProcessor();
      processor.expect(1, 3, true);
      processor.expect(0, 1, false);
      processor.expect(0, 2, true);
      SearchHandler handler = new SearchHandler("foo", "bar", processor);
      runHandler(handler, processor, "fo", "oxxxb", "aryyyfoo", "x", "xxbar");
   }

   private void runHandler(SearchHandler handler, ExpectProcessor processor, String... text) {
      Session session = SessionFactory.forTesting();
      HttpRequest request = session.httpRequestPool().acquire();
      handler.reserve(session);
      handler.beforeData(request);

      for (String t : text) {
         ByteBuf data = Unpooled.wrappedBuffer(t.getBytes(StandardCharsets.UTF_8));
         handler.handleData(request, data);
      }
      handler.afterData(request);
      processor.validate();
   }
}
