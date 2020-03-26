package io.hyperfoil.core.handlers;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.Test;

import io.hyperfoil.api.connection.HttpRequest;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.session.SessionFactory;

public class SearchValidatorTest {
   @Test
   public void testPositive() {
      SearchValidator validator = new SearchValidator("bar", m -> m == 1);
      HttpRequest request = runValidator(validator, "foobarfoo");
      assertThat(request.isValid()).isTrue();
   }

   @Test
   public void testNegative() {
      SearchValidator validator = new SearchValidator("bar", m -> m == 1);
      HttpRequest request = runValidator(validator, "foooo");
      assertThat(request.isValid()).isFalse();
   }

   @Test
   public void testStart() {
      SearchValidator validator = new SearchValidator("bar", m -> m == 1);
      HttpRequest request = runValidator(validator, "barfoo");
      assertThat(request.isValid()).isTrue();
   }

   @Test
   public void testEnd() {
      SearchValidator validator = new SearchValidator("bar", m -> m == 1);
      HttpRequest request = runValidator(validator, "foobar");
      assertThat(request.isValid()).isTrue();
   }

   @Test
   public void testSplit() {
      SearchValidator validator = new SearchValidator("bar", m -> m == 1);
      HttpRequest request = runValidator(validator, "foob", "arfoo");
      assertThat(request.isValid()).isTrue();
   }

   @Test
   public void testMany() {
      SearchValidator validator = new SearchValidator("bar", m -> m == 3);
      HttpRequest request = runValidator(validator, "foob", "arfoob", "a", "rfooba", "rfoo");
      assertThat(request.isValid()).isTrue();
   }

   @Test
   public void testOverlapping() {
      SearchValidator validator = new SearchValidator("barbar", m -> m == 1);
      HttpRequest request = runValidator(validator, "barbarbar");
      assertThat(request.isValid()).isTrue();
   }

   private HttpRequest runValidator(SearchValidator validator, String... text) {
      Session session = SessionFactory.forTesting();
      HttpRequest request = session.httpRequestPool().acquire();
      request.start(null, null);
      session.currentRequest(request);
      validator.reserve(session);
      validator.before(session);

      for (String t : text) {
         ByteBuf data = Unpooled.wrappedBuffer(t.getBytes(StandardCharsets.UTF_8));
         validator.process(session, data, data.readerIndex(), data.readableBytes(), false);
      }
      validator.after(session);
      return request;
   }
}
