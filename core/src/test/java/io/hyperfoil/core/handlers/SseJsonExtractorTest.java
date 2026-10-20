package io.hyperfoil.core.handlers;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.hyperfoil.api.config.Locator;
import io.hyperfoil.api.session.ObjectAccess;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.core.test.TestUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.vertx.core.json.JsonObject;

public class SseJsonExtractorTest {

   @BeforeEach
   public void before() {
      Locator.push(TestUtil.locator());
   }

   @AfterEach
   public void after() {
      Locator.pop();
   }

   @Test
   public void testExtractSimpleField() {
      String sseData = "data: {\"message\": \"hello\", \"status\": \"ok\"}\n";
      ObjectAccess messageAccess = SessionFactory.objectAccess("message");
      ObjectAccess stateAccess = SessionFactory.objectAccess("state");
      Session session = SessionFactory.forTesting(messageAccess, stateAccess);

      Map<String, String[]> targetPaths = new LinkedHashMap<>();
      targetPaths.put("message", new String[] { "message" });

      SseJsonExtractor extractor = new SseJsonExtractor(
            targetPaths,
            Collections.singletonList("[DONE]"),
            messageAccess,
            stateAccess,
            1048576);

      ResourceUtilizer.reserveForTesting(session, extractor);
      extractor.before(session);

      processSseData(extractor, session, sseData);

      assertThat(messageAccess.getObject(session)).isEqualTo("hello");
   }

   @Test
   public void testExtractMultipleFields() {
      String sseData = "data: {\"name\": \"John\", \"age\": \"30\", \"city\": \"NYC\"}\n";
      ObjectAccess resultAccess = SessionFactory.objectAccess("result");
      ObjectAccess stateAccess = SessionFactory.objectAccess("state");
      Session session = SessionFactory.forTesting(resultAccess, stateAccess);

      Map<String, String[]> targetPaths = new LinkedHashMap<>();
      targetPaths.put("name", new String[] { "name" });
      targetPaths.put("age", new String[] { "age" });

      SseJsonExtractor extractor = new SseJsonExtractor(
            targetPaths,
            Collections.singletonList("[DONE]"),
            resultAccess,
            stateAccess,
            1048576);

      ResourceUtilizer.reserveForTesting(session, extractor);
      extractor.before(session);

      processSseData(extractor, session, sseData);

      JsonObject result = (JsonObject) resultAccess.getObject(session);
      assertThat(result.getString("name")).isEqualTo("John");
      assertThat(result.getString("age")).isEqualTo("30");
   }

   @Test
   public void testExtractNestedField() {
      String sseData = "data: {\"user\": {\"name\": \"Alice\", \"id\": \"123\"}}\n";
      ObjectAccess nameAccess = SessionFactory.objectAccess("name");
      ObjectAccess stateAccess = SessionFactory.objectAccess("state");
      Session session = SessionFactory.forTesting(nameAccess, stateAccess);

      Map<String, String[]> targetPaths = new LinkedHashMap<>();
      targetPaths.put("name", new String[] { "user", "name" });

      SseJsonExtractor extractor = new SseJsonExtractor(
            targetPaths,
            Collections.singletonList("[DONE]"),
            nameAccess,
            stateAccess,
            1048576);

      ResourceUtilizer.reserveForTesting(session, extractor);
      extractor.before(session);

      processSseData(extractor, session, sseData);

      assertThat(nameAccess.getObject(session)).isEqualTo("Alice");
   }

   @Test
   public void testExtractArrayElement() {
      String sseData = "data: {\"items\": [\"first\", \"second\", \"third\"]}\n";
      ObjectAccess firstAccess = SessionFactory.objectAccess("first");
      ObjectAccess stateAccess = SessionFactory.objectAccess("state");
      Session session = SessionFactory.forTesting(firstAccess, stateAccess);

      Map<String, String[]> targetPaths = new LinkedHashMap<>();
      targetPaths.put("first", new String[] { "items", "0" });

      SseJsonExtractor extractor = new SseJsonExtractor(
            targetPaths,
            Collections.singletonList("[DONE]"),
            firstAccess,
            stateAccess,
            1048576);

      ResourceUtilizer.reserveForTesting(session, extractor);
      extractor.before(session);

      processSseData(extractor, session, sseData);

      assertThat(firstAccess.getObject(session)).isEqualTo("first");
   }

   @Test
   public void testMultipleSseEvents() {
      String sseData = "data: {\"count\": \"1\"}\n" +
            "data: {\"count\": \"2\"}\n" +
            "data: {\"count\": \"3\"}\n";
      ObjectAccess countAccess = SessionFactory.objectAccess("count");
      ObjectAccess stateAccess = SessionFactory.objectAccess("state");
      Session session = SessionFactory.forTesting(countAccess, stateAccess);

      Map<String, String[]> targetPaths = new LinkedHashMap<>();
      targetPaths.put("count", new String[] { "count" });

      SseJsonExtractor extractor = new SseJsonExtractor(
            targetPaths,
            Collections.singletonList("[DONE]"),
            countAccess,
            stateAccess,
            1048576);

      ResourceUtilizer.reserveForTesting(session, extractor);
      extractor.before(session);

      processSseData(extractor, session, sseData);

      assertThat(countAccess.getObject(session)).isEqualTo("123");
   }

   @Test
   public void testIgnorePatterns() {
      String sseData = "data: {\"type\": \"done\"}\n" +
            "data: {\"type\": \"progress\"}\n";
      ObjectAccess typeAccess = SessionFactory.objectAccess("type");
      ObjectAccess stateAccess = SessionFactory.objectAccess("state");
      Session session = SessionFactory.forTesting(typeAccess, stateAccess);

      Map<String, String[]> targetPaths = new LinkedHashMap<>();
      targetPaths.put("type", new String[] { "type" });

      List<String> ignorePatterns = new ArrayList<>(Collections.singletonList("{\"type\": \"done\"}"));

      SseJsonExtractor extractor = new SseJsonExtractor(
            targetPaths,
            ignorePatterns,
            typeAccess,
            stateAccess,
            1048576);

      ResourceUtilizer.reserveForTesting(session, extractor);
      extractor.before(session);

      processSseData(extractor, session, sseData);

      assertThat(typeAccess.getObject(session)).isEqualTo("progress");
   }

   @Test
   public void testEmptyJson() {
      String sseData = "data: {}\n";
      ObjectAccess valueAccess = SessionFactory.objectAccess("value");
      ObjectAccess stateAccess = SessionFactory.objectAccess("state");
      Session session = SessionFactory.forTesting(valueAccess, stateAccess);

      Map<String, String[]> targetPaths = new LinkedHashMap<>();
      targetPaths.put("value", new String[] { "missing" });

      SseJsonExtractor extractor = new SseJsonExtractor(
            targetPaths,
            Collections.singletonList("[DONE]"),
            valueAccess,
            stateAccess,
            1048576);

      ResourceUtilizer.reserveForTesting(session, extractor);
      extractor.before(session);

      processSseData(extractor, session, sseData);

      assertThat(valueAccess.getObject(session)).isEqualTo("");
   }

   @Test
   public void testWhitespaceHandling() {
      String sseData = "data:   {\"message\": \"test\"}\n";
      ObjectAccess messageAccess = SessionFactory.objectAccess("message");
      ObjectAccess stateAccess = SessionFactory.objectAccess("state");
      Session session = SessionFactory.forTesting(messageAccess, stateAccess);

      Map<String, String[]> targetPaths = new LinkedHashMap<>();
      targetPaths.put("message", new String[] { "message" });

      SseJsonExtractor extractor = new SseJsonExtractor(
            targetPaths,
            Collections.singletonList("[DONE]"),
            messageAccess,
            stateAccess,
            1048576);

      ResourceUtilizer.reserveForTesting(session, extractor);
      extractor.before(session);

      processSseData(extractor, session, sseData);

      assertThat(messageAccess.getObject(session)).isEqualTo("test");
   }

   @Test
   public void testCarriageReturnHandling() {
      String sseData = "data: {\"message\": \"hello\"}\r\n";
      ObjectAccess messageAccess = SessionFactory.objectAccess("message");
      ObjectAccess stateAccess = SessionFactory.objectAccess("state");
      Session session = SessionFactory.forTesting(messageAccess, stateAccess);

      Map<String, String[]> targetPaths = new LinkedHashMap<>();
      targetPaths.put("message", new String[] { "message" });

      SseJsonExtractor extractor = new SseJsonExtractor(
            targetPaths,
            Collections.singletonList("[DONE]"),
            messageAccess,
            stateAccess,
            1048576);

      ResourceUtilizer.reserveForTesting(session, extractor);
      extractor.before(session);

      processSseData(extractor, session, sseData);

      assertThat(messageAccess.getObject(session)).isEqualTo("hello");
   }

   @Test
   public void testComplexPath() {
      String sseData = "data: {\"response\": {\"data\": {\"items\": [{\"id\": \"123\", \"name\": \"test\"}]}}}\n";
      ObjectAccess nameAccess = SessionFactory.objectAccess("name");
      ObjectAccess stateAccess = SessionFactory.objectAccess("state");
      Session session = SessionFactory.forTesting(nameAccess, stateAccess);

      Map<String, String[]> targetPaths = new LinkedHashMap<>();
      targetPaths.put("name", new String[] { "response", "data", "items", "0", "name" });

      SseJsonExtractor extractor = new SseJsonExtractor(
            targetPaths,
            Collections.singletonList("[DONE]"),
            nameAccess,
            stateAccess,
            1048576);

      ResourceUtilizer.reserveForTesting(session, extractor);
      extractor.before(session);

      processSseData(extractor, session, sseData);

      assertThat(nameAccess.getObject(session)).isEqualTo("test");
   }

   @Test
   public void testMultipleEventsAccumulation() {
      String sseData = "data: {\"token\": \"abc\"}\n" +
            "data: {\"token\": \"def\"}\n" +
            "data: {\"token\": \"ghi\"}\n";
      ObjectAccess tokensAccess = SessionFactory.objectAccess("tokens");
      ObjectAccess stateAccess = SessionFactory.objectAccess("state");
      Session session = SessionFactory.forTesting(tokensAccess, stateAccess);

      Map<String, String[]> targetPaths = new LinkedHashMap<>();
      targetPaths.put("tokens", new String[] { "token" });

      SseJsonExtractor extractor = new SseJsonExtractor(
            targetPaths,
            Collections.singletonList("[DONE]"),
            tokensAccess,
            stateAccess,
            1048576);

      ResourceUtilizer.reserveForTesting(session, extractor);
      extractor.before(session);

      processSseData(extractor, session, sseData);

      assertThat(tokensAccess.getObject(session)).isEqualTo("abcdefghi");
   }

   @Test
   public void testDefaultIgnorePattern() {
      String sseData = "data: [DONE]\n" +
            "data: {\"message\": \"test\"}\n";
      ObjectAccess messageAccess = SessionFactory.objectAccess("message");
      ObjectAccess stateAccess = SessionFactory.objectAccess("state");
      Session session = SessionFactory.forTesting(messageAccess, stateAccess);

      Map<String, String[]> targetPaths = new LinkedHashMap<>();
      targetPaths.put("message", new String[] { "message" });

      SseJsonExtractor extractor = new SseJsonExtractor(
            targetPaths,
            Collections.singletonList("[DONE]"),
            messageAccess,
            stateAccess,
            1048576);

      ResourceUtilizer.reserveForTesting(session, extractor);
      extractor.before(session);

      processSseData(extractor, session, sseData);

      assertThat(messageAccess.getObject(session)).isEqualTo("test");
   }

   @Test
   public void testFragmentedNetworkStream() {
      // Split the payload in the middle of the JSON string
      String part1 = "data: {\"token\": \"hel";
      String part2 = "lo world\"}\n";

      ObjectAccess tokenAccess = SessionFactory.objectAccess("token");
      ObjectAccess stateAccess = SessionFactory.objectAccess("state");
      Session session = SessionFactory.forTesting(tokenAccess, stateAccess);

      Map<String, String[]> targetPaths = new LinkedHashMap<>();
      targetPaths.put("token", new String[] { "token" });

      SseJsonExtractor extractor = new SseJsonExtractor(
            targetPaths,
            Collections.singletonList("[DONE]"),
            tokenAccess,
            stateAccess,
            1048576);

      ResourceUtilizer.reserveForTesting(session, extractor);
      extractor.before(session);

      // Process Part 1 (isLastPart = false)
      byte[] data1 = part1.getBytes(StandardCharsets.UTF_8);
      ByteBuf buf1 = Unpooled.wrappedBuffer(data1);
      extractor.process(session, buf1, buf1.readerIndex(), buf1.readableBytes(), false);
      buf1.release();

      // Process Part 2 (isLastPart = true)
      byte[] data2 = part2.getBytes(StandardCharsets.UTF_8);
      ByteBuf buf2 = Unpooled.wrappedBuffer(data2);
      extractor.process(session, buf2, buf2.readerIndex(), buf2.readableBytes(), true);
      extractor.after(session);
      buf2.release();

      assertThat(tokenAccess.getObject(session)).isEqualTo("hello world");
   }

   private void processSseData(SseJsonExtractor extractor, Session session, String sseData) {
      byte[] data = sseData.getBytes(StandardCharsets.UTF_8);
      ByteBuf buf = Unpooled.wrappedBuffer(data);
      extractor.process(session, buf, buf.readerIndex(), buf.readableBytes(), true);
      extractor.after(session);
      buf.release();
   }
}
