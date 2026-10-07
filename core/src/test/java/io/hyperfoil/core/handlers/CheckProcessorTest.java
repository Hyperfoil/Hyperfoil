package io.hyperfoil.core.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.connection.Request;
import io.hyperfoil.api.processor.Processor;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.session.SessionFactory;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class CheckProcessorTest {
   @Test
   public void requiresExactlyOneStrategy() {
      assertThrows(BenchmarkDefinitionException.class, () -> new CheckProcessor.Builder().build(false));
      assertThrows(BenchmarkDefinitionException.class,
            () -> new CheckProcessor.Builder().equalTo("body").regex("body").build(false));
      assertThrows(BenchmarkDefinitionException.class,
            () -> new CheckProcessor.Builder().equalTo("body").json("\"body\"").build(false));
      assertThrows(BenchmarkDefinitionException.class,
            () -> new CheckProcessor.Builder().regex("body").json("\"body\"").build(false));
   }

   @Test
   public void rejectsRepeatedStrategies() {
      assertThrows(BenchmarkDefinitionException.class,
            () -> new CheckProcessor.Builder().equalTo("first").equalTo("second").build(false));
      assertThrows(BenchmarkDefinitionException.class,
            () -> new CheckProcessor.Builder().regex("first").regex("second").build(false));
      assertThrows(BenchmarkDefinitionException.class,
            () -> new CheckProcessor.Builder().json("1").json("2").build(false));
   }

   @Test
   public void equalToComparesTheCompleteBodyBytesIncludingEmptyBodiesAndOffsets() {
      assertValid(new CheckProcessor.Builder().equalTo(""), "");
      assertThat(validate(new CheckProcessor.Builder().equalTo("body"), 3, bytes("body")).isValid()).isTrue();
      assertInvalid(new CheckProcessor.Builder().equalTo("body"), "bod");
      assertInvalid(new CheckProcessor.Builder().equalTo("body"), "body ");
      assertInvalid(new CheckProcessor.Builder().equalTo("body"), "prefix-body");
      assertInvalid(new CheckProcessor.Builder().equalTo("\u00E9"), "e\u0301");
   }

   @Test
   public void equalToHandlesEveryUtf8ByteSplit() {
      String body = "A\u00E9\u20AC\uD83D\uDE00B";
      byte[] bytes = bytes(body);

      for (int split = 0; split <= bytes.length; ++split) {
         TestRequest request = validate(new CheckProcessor.Builder().equalTo(body), 0,
               Arrays.copyOfRange(bytes, 0, split), Arrays.copyOfRange(bytes, split, bytes.length));

         assertThat(request.isValid()).as("split at byte %d", split).isTrue();
      }
   }

   @Test
   public void regexUsesFullMatchesSemantics() {
      assertValid(new CheckProcessor.Builder().regex("a.c"), "abc");
      assertInvalid(new CheckProcessor.Builder().regex("a.c"), "prefix-abc-suffix");
      assertThat(validate(new CheckProcessor.Builder().regex("\uFFFD"), 0, new byte[] { (byte) 0xC3 }).isValid()).isTrue();
   }

   @Test
   public void jsonComparisonIgnoresObjectPropertyOrder() {
      assertValid(new CheckProcessor.Builder().json("{\"answer\":42,\"message\":\"ok\"}"),
            "{\"message\":\"ok\",\"answer\":42}");
   }

   @Test
   public void jsonComparisonIgnoresNestedObjectPropertyOrderAtEveryLevel() {
      assertValid(new CheckProcessor.Builder().json(
            "{\"first\":{\"second\":{\"answer\":42,\"enabled\":true},\"items\":[{\"id\":1,\"name\":\"one\"}]},\"message\":\"ok\"}"),
            "{\"message\":\"ok\",\"first\":{\"items\":[{\"name\":\"one\",\"id\":1}],\"second\":{\"enabled\":true,\"answer\":42}}}");
   }

   @Test
   public void jsonComparisonHandlesEveryFragmentSplit() {
      CheckProcessor.Builder builder = new CheckProcessor.Builder().json("{\"message\":\"ok\",\"values\":[1,true,null]}");
      byte[] actual = bytes("{\"values\":[1.0,true,null],\"message\":\"ok\"}");

      for (int split = 0; split <= actual.length; ++split) {
         TestRequest request = validate(builder, 0,
               Arrays.copyOfRange(actual, 0, split), Arrays.copyOfRange(actual, split, actual.length));
         assertThat(request.isValid()).as("split at byte %d", split).isTrue();
      }
   }

   @Test
   public void jsonComparisonPreservesArrayOrder() {
      assertInvalid(new CheckProcessor.Builder().json("[1,2,3]"), "[1,3,2]");
   }

   @Test
   public void jsonComparisonUsesMathematicalNumericEqualityWithoutFloatingPointRounding() {
      assertValid(new CheckProcessor.Builder().json("{\"number\":1}"), "{\"number\":1e0}");
      assertInvalid(new CheckProcessor.Builder().json("{\"number\":9007199254740992}"),
            "{\"number\":9007199254740993}");
   }

   @Test
   public void jsonComparisonRejectsNestedMissingExtraTypeAndArrayLengthMismatches() {
      CheckProcessor.Builder builder = new CheckProcessor.Builder().json(
            "{\"metadata\":{\"id\":7,\"values\":[\"one\",{\"valid\":true}]}}");

      assertInvalid(builder, "{\"metadata\":{\"values\":[\"one\",{\"valid\":true}]}}");
      assertInvalid(builder, "{\"metadata\":{\"id\":7,\"values\":[\"one\",{\"valid\":true}],\"extra\":false}}");
      assertInvalid(builder, "{\"metadata\":{\"id\":\"7\",\"values\":[\"one\",{\"valid\":true}]}}");
      assertInvalid(builder, "{\"metadata\":{\"id\":7,\"values\":[\"one\"]}}");
      assertInvalid(builder, "{\"metadata\":{\"id\":7,\"values\":[\"one\",{\"valid\":true},null]}}");
   }

   @Test
   public void jsonComparisonMatchesEveryTopLevelScalarAndEmptyContainer() {
      assertValid(new CheckProcessor.Builder().json("\"text\""), "\"text\"");
      assertValid(new CheckProcessor.Builder().json("123"), "123.0");
      assertValid(new CheckProcessor.Builder().json("true"), "true");
      assertValid(new CheckProcessor.Builder().json("false"), "false");
      assertValid(new CheckProcessor.Builder().json("null"), "null");
      assertValid(new CheckProcessor.Builder().json("{}"), "{}");
      assertValid(new CheckProcessor.Builder().json("[]"), "[]");
   }

   @Test
   public void jsonComparisonHandlesNegativeZeroLargeValuesAndDifferentNumericForms() {
      assertValid(new CheckProcessor.Builder().json("-0"), "0e-500");
      assertValid(new CheckProcessor.Builder().json("12345678901234567890123456789012345678901234567890"),
            "12345678901234567890123456789012345678901234567890.0");
      assertValid(new CheckProcessor.Builder().json("1.2300e3"), "1230");
   }

   @Test
   public void invalidActualJsonMarksTheRequestInvalidWithoutThrowing() {
      CheckProcessor.Builder builder = new CheckProcessor.Builder().json("{\"value\":false}");

      TestRequest malformed = assertDoesNotThrow(() -> validate(builder, 0, bytes("{\"value\":")));
      assertThat(malformed.isValid()).isFalse();
      assertInvalid(new CheckProcessor.Builder().json("{\"value\":false}"), "{\"value\":false} trailing");
      assertInvalid(new CheckProcessor.Builder().json("{\"value\":false}"),
            "{\"value\":true,\"value\":false}");
   }

   @Test
   public void jsonComparisonRejectsDeepDuplicateMalformedAndTrailingActualJson() {
      CheckProcessor.Builder builder = new CheckProcessor.Builder().json("{\"outer\":{\"inner\":{\"value\":false}}}");

      assertInvalid(builder, "{\"outer\":{\"inner\":{\"value\":false,\"value\":false}}}");
      assertInvalid(builder, "{\"outer\":{\"inner\":{\"value\":false}}");
      assertInvalid(builder, "{\"outer\":{\"inner\":{\"value\":false}}} null");
   }

   @Test
   public void jsonConfigurationRejectsDeepDuplicateMalformedAndTrailingValues() {
      assertInvalidExpectedJson("{\"outer\":{\"inner\":{\"value\":false,\"value\":false}}}");
      assertInvalidExpectedJson("{\"outer\":{\"inner\":[false,]}}");
      assertInvalidExpectedJson("{\"outer\":{\"inner\":{\"value\":false}}} null");
   }

   @Test
   public void jsonComparisonReadsNonZeroOffsets() {
      assertThat(validate(new CheckProcessor.Builder().json("{\"value\":[1,2,3]}"), 5,
            bytes("{\"value\":[1,2,3]}")).isValid()).isTrue();
   }

   @Test
   public void jsonProcessorSurvivesJavaSerializationRoundTrip() throws IOException, ClassNotFoundException {
      Processor processor = roundTrip(new CheckProcessor.Builder().json(
            "{\"outer\":{\"answer\":42,\"items\":[true,null,\"value\"]}}").build(false));

      assertThat(validate(processor, 0, bytes("{\"outer\":{\"items\":[true,null,\"value\"],\"answer\":42}}")).isValid())
            .isTrue();
   }

   @Test
   public void equalToProcessorSurvivesJavaSerializationRoundTrip() throws IOException, ClassNotFoundException {
      Processor processor = roundTrip(new CheckProcessor.Builder().equalTo("body").build(false));

      assertThat(validate(processor, 0, bytes("body")).isValid()).isTrue();
      assertThat(validate(processor, 0, bytes("bodY")).isValid()).isFalse();
   }

   @Test
   public void regexDotMatchesLineTerminators() {
      assertValid(new CheckProcessor.Builder().regex(".*"), "line1\nline2\r\n");
      assertValid(new CheckProcessor.Builder().regex("prefix-.*-suffix"), "prefix-\n-suffix");
      assertValid(new CheckProcessor.Builder().regex(".*"), "");
      assertThat(validate(new CheckProcessor.Builder().regex(".*").build(false), 0, new byte[0]).isValid()).isTrue();
   }

   @Test
   public void regexProcessorSurvivesJavaSerializationRoundTrip() throws IOException, ClassNotFoundException {
      Processor processor = roundTrip(new CheckProcessor.Builder().regex("a.c").build(false));

      assertThat(validate(processor, 0, bytes("a\nc")).isValid()).isTrue();
      assertThat(validate(processor, 0, bytes("abcd")).isValid()).isFalse();
   }

   @Test
   public void regexAndJsonReuseSessionResourcesAcrossRequestsAndGrowingBodies() {
      String longText = "x".repeat(1000) + "€";
      assertResults(new CheckProcessor.Builder().regex("x*€"), new boolean[] { true, false, true, true },
            bytes("x€"), bytes("y"), bytes(longText), bytes("€"));

      String longJson = "{\"value\":\"" + "y".repeat(1000) + "\"}";
      assertResults(new CheckProcessor.Builder().json("{\"value\":\"" + "y".repeat(1000) + "\"}"),
            new boolean[] { true, false, false, true },
            bytes(longJson), bytes("{\"value\":\"y\"}"), bytes("{\"value\":"), bytes(longJson));
   }

   @Test
   public void jsonNumbersOutsideLongRangeCompareMathematically() {
      assertValid(new CheckProcessor.Builder().json("9223372036854775808"), "9223372036854775808");
      assertValid(new CheckProcessor.Builder().json("9223372036854775808"), "9.223372036854775808e18");
      assertInvalid(new CheckProcessor.Builder().json("9223372036854775808"), "9223372036854775807");
      assertInvalid(new CheckProcessor.Builder().json("1"), "9223372036854775808");
      assertInvalid(new CheckProcessor.Builder().json("1.5"), "1");
      assertInvalid(new CheckProcessor.Builder().json("1.5"), "2");
   }

   @Test
   public void mismatchWithoutRequestInProgressDoesNotThrow() {
      Processor processor = new CheckProcessor.Builder().equalTo("expected").build(false);
      Session session = SessionFactory.forTesting();
      try {
         ResourceUtilizer.reserveForTesting(session, processor);
         ByteBuf data = Unpooled.wrappedBuffer(bytes("actual"));
         try {
            assertDoesNotThrow(() -> processor.process(session, data, 0, data.readableBytes(), true));
         } finally {
            data.release();
         }
      } finally {
         SessionFactory.destroy(session);
      }
   }

   private static void assertResults(CheckProcessor.Builder builder, boolean[] expected, byte[]... bodies) {
      Processor processor = builder.build(false);
      Session session = SessionFactory.forTesting();
      try {
         ResourceUtilizer.reserveForTesting(session, processor);
         for (int i = 0; i < bodies.length; ++i) {
            TestRequest request = new TestRequest(session);
            session.currentRequest(request);
            ByteBuf data = Unpooled.wrappedBuffer(bodies[i]);
            try {
               processor.before(session);
               processor.process(session, data, 0, bodies[i].length, true);
               processor.after(session);
            } finally {
               data.release();
            }
            assertThat(request.isValid()).as("body %d", i).isEqualTo(expected[i]);
         }
      } finally {
         SessionFactory.destroy(session);
      }
   }

   private static void assertValid(CheckProcessor.Builder builder, String actual) {
      assertThat(validate(builder, 0, bytes(actual)).isValid()).isTrue();
   }

   private static void assertInvalid(CheckProcessor.Builder builder, String actual) {
      assertThat(validate(builder, 0, bytes(actual)).isValid()).isFalse();
   }

   private static void assertInvalidExpectedJson(String expected) {
      assertThrows(BenchmarkDefinitionException.class, () -> new CheckProcessor.Builder().json(expected).build(false));
   }

   private static TestRequest validate(CheckProcessor.Builder builder, int offset, byte[]... fragments) {
      return validate(builder.build(true), offset, fragments);
   }

   private static TestRequest validate(Processor processor, int offset, byte[]... fragments) {
      Session session = SessionFactory.forTesting();
      TestRequest request = new TestRequest(session);
      session.currentRequest(request);
      ResourceUtilizer.reserveForTesting(session, processor);

      try {
         processor.before(session);
         for (int i = 0; i < fragments.length; ++i) {
            ByteBuf data = paddedBuffer(offset, fragments[i]);
            try {
               processor.process(session, data, offset, fragments[i].length, i == fragments.length - 1);
            } finally {
               data.release();
            }
         }
         processor.after(session);
         return request;
      } finally {
         SessionFactory.destroy(session);
      }
   }

   private static Processor roundTrip(Processor processor) throws IOException, ClassNotFoundException {
      byte[] serialized;
      try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ObjectOutputStream output = new ObjectOutputStream(bytes)) {
         output.writeObject(processor);
         output.flush();
         serialized = bytes.toByteArray();
      }
      try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
         return (Processor) input.readObject();
      }
   }

   private static ByteBuf paddedBuffer(int offset, byte[] bytes) {
      byte[] padded = new byte[offset + bytes.length + 2];
      System.arraycopy(bytes, 0, padded, offset, bytes.length);
      return Unpooled.wrappedBuffer(padded);
   }

   private static byte[] bytes(String value) {
      return value.getBytes(StandardCharsets.UTF_8);
   }

   private static class TestRequest extends Request {
      private TestRequest(Session session) {
         super(session);
      }

      @Override
      public void release() {
      }

      @Override
      public long getStartTimestampMillis(Session session) {
         return startTimestampMillis();
      }

      @Override
      public long getStartTimestampNanos(Session session) {
         return startTimestampNanos();
      }
   }
}
