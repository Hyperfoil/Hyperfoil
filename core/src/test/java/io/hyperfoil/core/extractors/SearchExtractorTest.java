package io.hyperfoil.core.extractors;

import java.nio.charset.StandardCharsets;

import org.junit.Test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.hyperfoil.api.connection.Request;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.session.SessionFactory;

public class SearchExtractorTest {
   @Test
   public void testSimple() {
      ExpectProcessor processor = new ExpectProcessor();
      processor.expect(6, 3, true);
      SearchExtractor extractor = new SearchExtractor("foo", "bar", processor);
      runExtractor(extractor, processor, "yyyfooxxxbaryyy");
   }

   @Test
   public void testStartEnd() {
      ExpectProcessor processor = new ExpectProcessor();
      processor.expect(3, 2, true);
      SearchExtractor extractor = new SearchExtractor("foo", "bar", processor);
      runExtractor(extractor, processor, "fooxxbar");
   }

   @Test
   public void testEmpty() {
      ExpectProcessor processor = new ExpectProcessor();
      processor.expect(3, 0, true);
      SearchExtractor extractor = new SearchExtractor("foo", "bar", processor);
      runExtractor(extractor, processor, "foobar");
   }

   @Test
   public void testNotEnding() {
      ExpectProcessor processor = new ExpectProcessor();
      SearchExtractor extractor = new SearchExtractor("foo", "bar", processor);
      runExtractor(extractor, processor, "fooxxx");
   }

   @Test
   public void testGreedy() {
      ExpectProcessor processor = new ExpectProcessor();
      processor.expect(3, 6, true);
      SearchExtractor extractor = new SearchExtractor("foo", "bar", processor);
      runExtractor(extractor, processor, "foofooxxxbar");
   }

   @Test
   public void testSplitMany() {
      ExpectProcessor processor = new ExpectProcessor();
      processor.expect(1, 3, true);
      processor.expect(0, 1, false);
      processor.expect(0, 2, true);
      SearchExtractor extractor = new SearchExtractor("foo", "bar", processor);
      runExtractor(extractor, processor, "fo", "oxxxb", "aryyyfoo", "x", "xxbar");
   }

   private void runExtractor(SearchExtractor extractor, ExpectProcessor processor, String... text) {
      Session session = SessionFactory.forTesting();
      Request request = session.requestPool().acquire();
      extractor.reserve(session);
      extractor.beforeData(request);

      for (String t : text) {
         ByteBuf data = Unpooled.wrappedBuffer(t.getBytes(StandardCharsets.UTF_8));
         extractor.extractData(request, data);
      }
      extractor.afterData(request);
      processor.validate();
   }
}
