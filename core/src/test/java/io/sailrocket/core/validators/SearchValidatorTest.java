package io.sailrocket.core.validators;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.Test;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.sailrocket.api.session.Session;
import io.sailrocket.core.session.SessionFactory;

public class SearchValidatorTest {
   @Test
   public void testPositive() {
      SearchValidator validator = new SearchValidator("bar", m -> m == 1);
      Session session = runValidator(validator, "foobarfoo");
      assertThat(validator.validate(session)).isTrue();
   }

   @Test
   public void testNegative() {
      SearchValidator validator = new SearchValidator("bar", m -> m == 1);
      Session session = runValidator(validator, "foooo");
      assertThat(validator.validate(session)).isFalse();
   }

   @Test
   public void testStart() {
      SearchValidator validator = new SearchValidator("bar", m -> m == 1);
      Session session = runValidator(validator, "barfoo");
      assertThat(validator.validate(session)).isTrue();
   }

   @Test
   public void testEnd() {
      SearchValidator validator = new SearchValidator("bar", m -> m == 1);
      Session session = runValidator(validator, "foobar");
      assertThat(validator.validate(session)).isTrue();
   }

   @Test
   public void testSplit() {
      SearchValidator validator = new SearchValidator("bar", m -> m == 1);
      Session session = runValidator(validator, "foob", "arfoo");
      assertThat(validator.validate(session)).isTrue();
   }

   @Test
   public void testMany() {
      SearchValidator validator = new SearchValidator("bar", m -> m == 3);
      Session session = runValidator(validator, "foob", "arfoob", "a", "rfooba", "rfoo");
      assertThat(validator.validate(session)).isTrue();
   }

   @Test
   public void testOverlapping() {
      SearchValidator validator = new SearchValidator("barbar", m -> m == 1);
      Session session = runValidator(validator, "barbarbar");
      assertThat(validator.validate(session)).isTrue();
   }

   private Session runValidator(SearchValidator validator, String... text) {
      Session session = SessionFactory.forTesting();
      validator.reserve(session);
      validator.beforeData(session);

      for (String t : text) {
         ByteBuf data = Unpooled.wrappedBuffer(t.getBytes(StandardCharsets.UTF_8));
         validator.validateData(session, data);
      }
      return session;
   }
}
