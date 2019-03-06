package io.hyperfoil.core.validators;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.Test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.hyperfoil.api.connection.Request;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.session.SessionFactory;

public class SearchValidatorTest {
   @Test
   public void testPositive() {
      SearchValidator validator = new SearchValidator("bar", m -> m == 1);
      Request request = runValidator(validator, "foobarfoo");
      assertThat(validator.validate(request)).isTrue();
   }

   @Test
   public void testNegative() {
      SearchValidator validator = new SearchValidator("bar", m -> m == 1);
      Request request = runValidator(validator, "foooo");
      assertThat(validator.validate(request)).isFalse();
   }

   @Test
   public void testStart() {
      SearchValidator validator = new SearchValidator("bar", m -> m == 1);
      Request request = runValidator(validator, "barfoo");
      assertThat(validator.validate(request)).isTrue();
   }

   @Test
   public void testEnd() {
      SearchValidator validator = new SearchValidator("bar", m -> m == 1);
      Request request = runValidator(validator, "foobar");
      assertThat(validator.validate(request)).isTrue();
   }

   @Test
   public void testSplit() {
      SearchValidator validator = new SearchValidator("bar", m -> m == 1);
      Request request = runValidator(validator, "foob", "arfoo");
      assertThat(validator.validate(request)).isTrue();
   }

   @Test
   public void testMany() {
      SearchValidator validator = new SearchValidator("bar", m -> m == 3);
      Request request = runValidator(validator, "foob", "arfoob", "a", "rfooba", "rfoo");
      assertThat(validator.validate(request)).isTrue();
   }

   @Test
   public void testOverlapping() {
      SearchValidator validator = new SearchValidator("barbar", m -> m == 1);
      Request request = runValidator(validator, "barbarbar");
      assertThat(validator.validate(request)).isTrue();
   }

   private Request runValidator(SearchValidator validator, String... text) {
      Session session = SessionFactory.forTesting();
      Request request = session.httpRequestPool().acquire();
      validator.reserve(session);
      validator.beforeData(request);

      for (String t : text) {
         ByteBuf data = Unpooled.wrappedBuffer(t.getBytes(StandardCharsets.UTF_8));
         validator.validateData(request, data);
      }
      return request;
   }
}
