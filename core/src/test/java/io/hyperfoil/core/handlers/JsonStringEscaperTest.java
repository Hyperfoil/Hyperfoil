package io.hyperfoil.core.handlers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.hyperfoil.api.config.Locator;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.session.ObjectAccess;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.core.test.TestUtil;

public class JsonStringEscaperTest {

   @BeforeEach
   public void before() {
      Locator.push(TestUtil.locator());
   }

   @AfterEach
   public void after() {
      Locator.pop();
   }

   @Test
   public void testNoEscapingNeeded() {
      assertEscape("hello", "hello");
      assertEscape("simple_string", "simple_string");
      assertEscape("123", "123");
      assertEscape("", "");
   }

   @Test
   public void testQuoteEscaping() {
      assertEscape("hello\"world", "hello\\\"world");
      assertEscape("\"", "\\\"");
      assertEscape("\"hello\"", "\\\"hello\\\"");
   }

   @Test
   public void testBackslashEscaping() {
      assertEscape("hello\\world", "hello\\\\world");
      assertEscape("\\", "\\\\");
      assertEscape("\\\\", "\\\\\\\\");
   }

   @Test
   public void testForwardSlashEscaping() {
      assertEscape("hello/world", "hello\\/world");
      assertEscape("/", "\\/");
      assertEscape("///", "\\/\\/\\/");
   }

   @Test
   public void testNewlineEscaping() {
      assertEscape("hello\nworld", "hello\\nworld");
      assertEscape("\n", "\\n");
      assertEscape("line1\nline2\nline3", "line1\\nline2\\nline3");
   }

   @Test
   public void testCarriageReturnEscaping() {
      assertEscape("hello\rworld", "hello\\rworld");
      assertEscape("\r", "\\r");
   }

   @Test
   public void testTabEscaping() {
      assertEscape("hello\tworld", "hello\\tworld");
      assertEscape("\t", "\\t");
      assertEscape("col1\tcol2", "col1\\tcol2");
   }

   @Test
   public void testBackspaceEscaping() {
      assertEscape("hello\bworld", "hello\\bworld");
      assertEscape("\b", "\\b");
   }

   @Test
   public void testFormFeedEscaping() {
      assertEscape("hello\fworld", "hello\\fworld");
      assertEscape("\f", "\\f");
   }

   @Test
   public void testControlCharacterEscaping() {
      assertEscape("hello\u0000world", "hello\\u0000world");
      assertEscape("hello\u001fworld", "hello\\u001fworld");
      assertEscape("\u0001", "\\u0001");
      assertEscape("\u001e", "\\u001e");
   }

   @Test
   public void testMultipleEscapeSequences() {
      assertEscape("line1\nline2\twith\"quote", "line1\\nline2\\twith\\\"quote");
      assertEscape("path/to\\file", "path\\/to\\\\file");
      assertEscape("\"string\\with/multiple'chars\n", "\\\"string\\\\with\\/multiple'chars\\n");
   }

   @Test
   public void testMixedSpecialCharacters() {
      assertEscape("{\n  \"key\": \"value\"\n}", "{\\n  \\\"key\\\": \\\"value\\\"\\n}");
      assertEscape("\\\"hello\\\"", "\\\\\\\"hello\\\\\\\"");
   }

   @Test
   public void testNonStringObject() {
      ObjectAccess sourceAccess = SessionFactory.objectAccess("source");
      ObjectAccess targetAccess = SessionFactory.objectAccess("target");
      Session session = SessionFactory.forTesting(sourceAccess, targetAccess);
      sourceAccess.setObject(session, 12345);
      JsonStringEscaper escaper = new JsonStringEscaper(sourceAccess, targetAccess);
      ResourceUtilizer.reserveForTesting(session, escaper);
      escaper.invoke(session);
      assertThat(targetAccess.getObject(session)).isEqualTo("12345");
   }

   @Test
   public void testBuilderSuccess() {
      java.util.List<Step> steps = new JsonStringEscaper.Builder()
            .fromVar("source")
            .toVar("target")
            .build();
      assertThat(steps).hasSize(1);
      assertThat(steps.get(0)).isInstanceOf(JsonStringEscaper.class);
   }

   @Test
   public void testNullObject() {
      ObjectAccess sourceAccess = SessionFactory.objectAccess("source");
      ObjectAccess targetAccess = SessionFactory.objectAccess("target");
      Session session = SessionFactory.forTesting(sourceAccess, targetAccess);

      // Explicitly set the source to null
      sourceAccess.setObject(session, null);

      JsonStringEscaper escaper = new JsonStringEscaper(sourceAccess, targetAccess);
      ResourceUtilizer.reserveForTesting(session, escaper);
      boolean result = escaper.invoke(session);

      // The step should succeed, but the target variable should remain unset
      assertThat(result).isTrue();
      assertThat(targetAccess.isSet(session)).isFalse();
   }

   private void assertEscape(String input, String expectedOutput) {
      ObjectAccess sourceAccess = SessionFactory.objectAccess("source");
      ObjectAccess targetAccess = SessionFactory.objectAccess("target");
      Session session = SessionFactory.forTesting(sourceAccess, targetAccess);
      sourceAccess.setObject(session, input);
      JsonStringEscaper escaper = new JsonStringEscaper(sourceAccess, targetAccess);
      ResourceUtilizer.reserveForTesting(session, escaper);
      boolean result = escaper.invoke(session);
      assertThat(result).isTrue();
      assertThat(targetAccess.getObject(session)).isEqualTo(expectedOutput);
   }
}
