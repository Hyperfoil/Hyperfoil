package io.hyperfoil.core.generators;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.hyperfoil.api.config.Locator;
import io.hyperfoil.api.session.IntAccess;
import io.hyperfoil.api.session.ObjectAccess;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.core.test.TestUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

public class PatternTest {
   @Before
   public void before() {
      Locator.push(TestUtil.locator());
   }

   @After
   public void after() {
      Locator.pop();
   }

   @Test
   public void testString() {
      Pattern pattern = new Pattern("foo${var}bar", false);
      Session session = setObject("var", "xx");
      test(pattern, session, "fooxxbar");
   }

   @Test
   public void testNonAscii() {
      Pattern pattern = new Pattern("foo${var}bar", false);
      Session session = setObject("var", "ěščř");
      test(pattern, session, "fooěščřbar");
   }

   @Test
   public void testInt() {
      Pattern pattern = new Pattern("${var}bar", false);
      Session session = setInt("var", 42);
      test(pattern, session, "42bar");
   }

   @Test
   public void testFormat() {
      Pattern pattern = new Pattern("${%04X:var}bar", false);
      Session session = setInt("var", 42);
      test(pattern, session, "002Abar");
   }

   @Test
   public void testUrlEncode() {
      Pattern pattern = new Pattern("foo${urlencode:var}", false);
      Session session = setObject("var", " @+ěščř ");
      test(pattern, session, "foo+%40%2B%C4%9B%C5%A1%C4%8D%C5%99+");
   }

   @Test
   public void testUrlEncodeImplicit() {
      Pattern pattern = new Pattern("foo${var}", true);
      Session session = setObject("var", " @+ěščř ");
      test(pattern, session, "foo+%40%2B%C4%9B%C5%A1%C4%8D%C5%99+");
   }

   @Test
   public void testEscape() {
      Pattern pattern = new Pattern("foo${var}$${var}${var}$$${var}", false);
      Session session = setObject("var", "bar");
      test(pattern, session, "foobar${var}bar$${var}");
   }

   @Test
   public void testReplaceFirst() {
      Pattern pattern = new Pattern("foo${replace/[a-z]/X/:var}bar", false);
      Session session = setObject("var", "xyz");
      test(pattern, session, "fooXyzbar");
   }

   @Test
   public void testReplaceAll() {
      Pattern pattern = new Pattern("foo${replace#[a-z]#X#g:var}bar", false);
      Session session = setObject("var", "xyz");
      test(pattern, session, "fooXXXbar");
   }

   private Session setObject(String name, String value) {
      ObjectAccess var = SessionFactory.objectAccess(name);
      Session session = SessionFactory.forTesting(var);
      var.setObject(session, value);
      return session;
   }

   private Session setInt(String name, int value) {
      IntAccess var = SessionFactory.intAccess(name);
      Session session = SessionFactory.forTesting(var);
      var.setInt(session, value);
      return session;
   }

   private void test(Pattern pattern, Session session, String expected) {
      TestUtil.resolveAccess(session, pattern);
      String str = pattern.apply(session);
      assertThat(str).isEqualTo(expected);
      ByteBuf buf = ByteBufAllocator.DEFAULT.buffer();
      pattern.accept(session, buf);
      byte[] bytes = new byte[buf.readableBytes()];
      buf.readBytes(bytes);
      String bufString = new String(bytes, StandardCharsets.UTF_8);
      assertThat(bufString).isEqualTo(expected);
      buf.release();
   }
}
