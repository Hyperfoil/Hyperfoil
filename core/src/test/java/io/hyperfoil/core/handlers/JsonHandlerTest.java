package io.hyperfoil.core.handlers;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.hyperfoil.api.processor.Processor;
import io.hyperfoil.api.processor.Transformer;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.handlers.json.JsonHandler;
import io.hyperfoil.core.handlers.json.JsonUnquotingTransformer;
import io.hyperfoil.core.session.SessionFactory;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class JsonHandlerTest {
   private static final byte[] ID418 = "418".getBytes(StandardCharsets.UTF_8);
   private static final byte[] ID420 = "420".getBytes(StandardCharsets.UTF_8);
   private static final byte[] ID450 = "450".getBytes(StandardCharsets.UTF_8);
   private static final byte[] JSON = ("[\n" +
         "      { \"id\" : 418, \"product\" : \"Teapots\", \"units\" : 123 },\n" +
         "      { \"id\" : 420, \"product\" : \"Various herbs\", \"units\" : 321  },\n" +
         "      { \"id\" : 450, \"product\" : \"Magazines\", \"units\": 456 }\n" +
         "    ]").getBytes(StandardCharsets.UTF_8);
   private static final byte[] ESCAPED = ("[\n" +
         "      { \"foo\" : \"\\nx\\bx\\f\\rx\\t\" },\n" +
         "      { \"foo\" : \"x\\u15dCx\\\"x\\/\\\\\" },\n" +
         "    ]").getBytes(StandardCharsets.UTF_8);

   @Test
   public void testFull() {
      ExpectProcessor expect = new ExpectProcessor()
            .expect(-1, 3, true)
            .expect(-1, 3, true)
            .expect(-1, 3, true);
      JsonHandler handler = new JsonHandler(".[].id", false, null, expect);
      Session session = SessionFactory.forTesting();
      ResourceUtilizer.reserveForTesting(session, handler);

      ByteBuf data = Unpooled.wrappedBuffer(JSON);

      handler.before(session);
      handler.process(session, data, data.readerIndex(), data.readableBytes(), true);
      handler.after(session);

      expect.validate();
   }

   @Test
   public void testSplit() {
      ExpectProcessor expect = new ExpectProcessor();
      JsonHandler handler = new JsonHandler(".[].id", false, null, expect);
      Session session = SessionFactory.forTesting();
      ResourceUtilizer.reserveForTesting(session, handler);

      for (int i = 0; i < JSON.length; ++i) {
         ByteBuf data1 = Unpooled.wrappedBuffer(JSON, 0, i);
         ByteBuf data2 = Unpooled.wrappedBuffer(JSON, i, JSON.length - i);

         for (byte[] string : new byte[][] { ID418, ID420, ID450 }) {
            if (contains(JSON, 0, i, string) || contains(JSON, i, JSON.length - i, string)) {
               expect.expect(-1, 3, true);
            } else {
               expect.expect(-1, -1, false);
               expect.expect(-1, -1, true);
            }
         }

         handler.before(session);
         handler.process(session, data1, data1.readerIndex(), data1.readableBytes(), false);
         handler.process(session, data2, data2.readerIndex(), data2.readableBytes(), true);
         handler.after(session);

         expect.validate();
      }
   }

   @Test
   public void testSelectObject() {
      ExpectProcessor expect = new ExpectProcessor()
            .expect(9, 14, true);
      JsonHandler handler = new JsonHandler(".foo", false, null, expect);
      Session session = SessionFactory.forTesting();
      ResourceUtilizer.reserveForTesting(session, handler);

      ByteBuf data = Unpooled.wrappedBuffer("{ \"foo\": { \"bar\" : 42 }}".getBytes(StandardCharsets.UTF_8));

      handler.before(session);
      handler.process(session, data, data.readerIndex(), data.readableBytes(), true);
      handler.after(session);

      expect.validate();
   }

   @Test
   public void testEscaped() {
      List<String> unescapedItems = Arrays.asList("\nx\bx\f\rx\t", "x\u15dcx\"x/\\");
      List<String> expectedStrings = new ArrayList<>(unescapedItems);
      Processor expect = (Processor) (session, data, offset, length, isLastPart) -> {
         byte[] bytes = new byte[length];
         data.getBytes(offset, bytes);
         String str = new String(bytes, StandardCharsets.UTF_8);
         assertThat(str).isEqualTo(expectedStrings.remove(0));
      };
      JsonHandler handler = new JsonHandler(".[].foo", false, null, new JsonUnquotingTransformer(new DefragProcessor(expect)));
      Session session = SessionFactory.forTesting();
      ResourceUtilizer.reserveForTesting(session, handler);

      for (int i = 0; i < ESCAPED.length; ++i) {
         handleSplit(handler, session, ESCAPED, i);

         assertThat(expectedStrings.isEmpty()).isTrue();
         expectedStrings.addAll(unescapedItems);
      }
   }

   @Test
   public void testDelete() {
      StringCollector stringCollector = new StringCollector();
      JsonHandler handler = new JsonHandler(".[].product", true, null, new DefragProcessor(stringCollector));
      Session session = SessionFactory.forTesting();
      ResourceUtilizer.reserveForTesting(session, handler);

      for (int i = 0; i < JSON.length; ++i) {
         handleSplit(handler, session, JSON, i);

         JsonArray array = (JsonArray) Json.decodeValue(stringCollector.str);
         assertThat(array.size()).isEqualTo(3);
         array.forEach(o -> {
            JsonObject obj = (JsonObject) o;
            assertThat(obj.getInteger("id")).isNotNull();
            assertThat(obj.getInteger("units")).isNotNull();
         });
      }
   }

   @Test
   public void testDeleteArrayItem() {
      StringCollector stringCollector = new StringCollector();
      JsonHandler handler = new JsonHandler(".[1]", true, null, new DefragProcessor(stringCollector));
      Session session = SessionFactory.forTesting();
      ResourceUtilizer.reserveForTesting(session, handler);

      for (int i = 0; i < JSON.length; ++i) {
         handleSplit(handler, session, JSON, i);

         JsonArray array = (JsonArray) Json.decodeValue(stringCollector.str);
         assertThat(array.size()).isEqualTo(2);
         array.forEach(o -> {
            JsonObject obj = (JsonObject) o;
            assertThat(obj.getInteger("id")).isNotNull();
            assertThat(obj.getString("product")).isNotBlank();
            assertThat(obj.getInteger("units")).isNotNull();
         });
      }
   }

   @Test
   public void testReplace() {
      StringCollector stringCollector = new StringCollector();
      JsonUnquotingTransformer replace = new JsonUnquotingTransformer(new ObscuringTransformer());
      JsonHandler handler = new JsonHandler(".[].product", false, replace, new DefragProcessor(stringCollector));
      Session session = SessionFactory.forTesting();
      ResourceUtilizer.reserveForTesting(session, handler);

      for (int i = 0; i < JSON.length; ++i) {
         handleSplit(handler, session, JSON, i);

         JsonArray array = (JsonArray) Json.decodeValue(stringCollector.str);
         assertThat(array.size()).isEqualTo(3);
         array.forEach(o -> {
            JsonObject obj = (JsonObject) o;
            assertThat(obj.getInteger("id")).isNotNull();
            assertThat(obj.getInteger("units")).isNotNull();
         });
         assertThat(array.getJsonObject(0).getString("product")).isEqualTo("xxxxxxx");
         assertThat(array.getJsonObject(1).getString("product")).isEqualTo("xxxxxxxxxxxxx");
         assertThat(array.getJsonObject(2).getString("product")).isEqualTo("xxxxxxxxx");
      }
   }

   @Test
   public void testReplaceDeleting() {
      StringCollector stringCollector = new StringCollector();
      JsonHandler handler = new JsonHandler(".[1]", false, (Transformer) (session, in, offset, length, lastFragment, out) -> {
      }, new DefragProcessor(stringCollector));
      Session session = SessionFactory.forTesting();
      ResourceUtilizer.reserveForTesting(session, handler);

      for (int i = 0; i < JSON.length; ++i) {
         handleSplit(handler, session, JSON, i);

         JsonArray array = (JsonArray) Json.decodeValue(stringCollector.str);
         assertThat(array.size()).isEqualTo(2);
         array.forEach(o -> {
            JsonObject obj = (JsonObject) o;
            assertThat(obj.getInteger("id")).isNotNull();
            assertThat(obj.getString("product")).isNotBlank();
            assertThat(obj.getInteger("units")).isNotNull();
         });
      }
   }

   @Test
   public void testDeleteMultiFragment() {
      // Delete with many small fragments — exercises StreamQueue release behavior
      // across many parts, verifying that lastOutputIndex data is not prematurely freed
      for (int chunkSize : new int[] { 1, 2, 3, 5, 7, 11 }) {
         StringCollector stringCollector = new StringCollector();
         JsonHandler handler = new JsonHandler(".[].product", true, null, new DefragProcessor(stringCollector));
         Session session = SessionFactory.forTesting();
         ResourceUtilizer.reserveForTesting(session, handler);

         handler.before(session);
         int pos = 0;
         while (pos < JSON.length) {
            int len = Math.min(chunkSize, JSON.length - pos);
            boolean isLast = (pos + len >= JSON.length);
            ByteBuf buf = Unpooled.wrappedBuffer(JSON, pos, len);
            handler.process(session, buf, buf.readerIndex(), buf.readableBytes(), isLast);
            pos += len;
         }
         handler.after(session);

         JsonArray array = (JsonArray) Json.decodeValue(stringCollector.str);
         assertThat(array.size()).as("chunkSize=%d", chunkSize).isEqualTo(3);
         array.forEach(o -> {
            JsonObject obj = (JsonObject) o;
            assertThat(obj.getInteger("id")).isNotNull();
            assertThat(obj.getInteger("units")).isNotNull();
         });
      }
   }

   @Test
   public void testReplaceMultiFragment() {
      // Replace with many small fragments — same concern as delete
      for (int chunkSize : new int[] { 1, 2, 3, 5, 7, 11 }) {
         StringCollector stringCollector = new StringCollector();
         JsonUnquotingTransformer replace = new JsonUnquotingTransformer(new ObscuringTransformer());
         JsonHandler handler = new JsonHandler(".[].product", false, replace, new DefragProcessor(stringCollector));
         Session session = SessionFactory.forTesting();
         ResourceUtilizer.reserveForTesting(session, handler);

         handler.before(session);
         int pos = 0;
         while (pos < JSON.length) {
            int len = Math.min(chunkSize, JSON.length - pos);
            boolean isLast = (pos + len >= JSON.length);
            ByteBuf buf = Unpooled.wrappedBuffer(JSON, pos, len);
            handler.process(session, buf, buf.readerIndex(), buf.readableBytes(), isLast);
            pos += len;
         }
         handler.after(session);

         JsonArray array = (JsonArray) Json.decodeValue(stringCollector.str);
         assertThat(array.size()).as("chunkSize=%d", chunkSize).isEqualTo(3);
         array.forEach(o -> {
            JsonObject obj = (JsonObject) o;
            assertThat(obj.getInteger("id")).isNotNull();
            assertThat(obj.getInteger("units")).isNotNull();
         });
         assertThat(array.getJsonObject(0).getString("product")).as("chunkSize=%d", chunkSize).isEqualTo("xxxxxxx");
         assertThat(array.getJsonObject(1).getString("product")).as("chunkSize=%d", chunkSize).isEqualTo("xxxxxxxxxxxxx");
         assertThat(array.getJsonObject(2).getString("product")).as("chunkSize=%d", chunkSize).isEqualTo("xxxxxxxxx");
      }
   }

   private void handleSplit(JsonHandler handler, Session session, byte[] json, int position) {
      ByteBuf data1 = Unpooled.wrappedBuffer(json, 0, position);
      ByteBuf data2 = Unpooled.wrappedBuffer(json, position, json.length - position);

      handler.before(session);
      handler.process(session, data1, data1.readerIndex(), data1.readableBytes(), false);
      handler.process(session, data2, data2.readerIndex(), data2.readableBytes(), true);
      handler.after(session);
   }

   @Test
   public void testSelectQuotedStringValues() {
      // Test .[].name with quoted string values - split at every possible position
      byte[] json = "[{\"id\":0,\"name\":\"alice\"},{\"id\":1,\"name\":\"bob\"}]".getBytes(StandardCharsets.UTF_8);

      for (int i = 0; i <= json.length; ++i) {
         List<String> values = new ArrayList<>();
         Processor collector = (session, data, offset, length, isLastPart) -> {
            byte[] bytes = new byte[length];
            data.getBytes(offset, bytes);
            values.add(new String(bytes, StandardCharsets.UTF_8));
         };
         JsonHandler handler = new JsonHandler(".[].name", false, null, new DefragProcessor(collector));
         Session session = SessionFactory.forTesting();
         ResourceUtilizer.reserveForTesting(session, handler);

         handler.before(session);
         if (i == json.length) {
            // No split - single buffer
            ByteBuf data = Unpooled.wrappedBuffer(json);
            handler.process(session, data, data.readerIndex(), data.readableBytes(), true);
         } else {
            ByteBuf data1 = Unpooled.wrappedBuffer(json, 0, i);
            ByteBuf data2 = Unpooled.wrappedBuffer(json, i, json.length - i);
            handler.process(session, data1, data1.readerIndex(), data1.readableBytes(), false);
            handler.process(session, data2, data2.readerIndex(), data2.readableBytes(), true);
         }
         handler.after(session);

         assertThat(values).as("split at %d of '%s'", i, new String(json, 0, i, StandardCharsets.UTF_8))
               .containsExactly("\"alice\"", "\"bob\"");
      }
   }

   @Test
   public void testSelectQuotedStringValuesMultiFragment() {
      // Test with many small fragments to simulate TCP fragmentation
      StringBuilder sb = new StringBuilder("[");
      int count = 100;
      for (int i = 0; i < count; i++) {
         if (i > 0)
            sb.append(",");
         sb.append("{\"id\":").append(i).append(",\"name\":\"item").append(i).append("\"}");
      }
      sb.append("]");
      byte[] json = sb.toString().getBytes(StandardCharsets.UTF_8);

      // Split into chunks of varying sizes
      for (int chunkSize : new int[] { 10, 17, 31, 64, 128 }) {
         List<String> values = new ArrayList<>();
         Processor collector = (session, data, offset, length, isLastPart) -> {
            byte[] bytes = new byte[length];
            data.getBytes(offset, bytes);
            values.add(new String(bytes, StandardCharsets.UTF_8));
         };
         JsonHandler handler = new JsonHandler(".[].name", false, null, new DefragProcessor(collector));
         Session session = SessionFactory.forTesting();
         ResourceUtilizer.reserveForTesting(session, handler);

         handler.before(session);
         int pos = 0;
         while (pos < json.length) {
            int len = Math.min(chunkSize, json.length - pos);
            boolean isLast = (pos + len >= json.length);
            ByteBuf buf = Unpooled.wrappedBuffer(json, pos, len);
            handler.process(session, buf, buf.readerIndex(), buf.readableBytes(), isLast);
            pos += len;
         }
         handler.after(session);

         assertThat(values).as("chunkSize=%d", chunkSize).hasSize(count);
      }
   }

   private boolean contains(byte[] data, int offset, int length, byte[] string) {
      OUTER: for (int i = 0; i <= length - string.length; ++i) {
         for (int j = 0; j < string.length && i + j < length; ++j) {
            if (string[j] != data[offset + i + j]) {
               continue OUTER;
            }
         }
         return true;
      }
      return false;
   }

   private static class StringCollector implements Processor {
      private String str;

      @Override
      public void before(Session session) {
         str = "This was never set";
      }

      @Override
      public void process(Session session, ByteBuf data, int offset, int length, boolean isLastPart) {
         byte[] bytes = new byte[length];
         data.getBytes(offset, bytes);
         str = new String(bytes, StandardCharsets.UTF_8);
      }
   }

   private static class ObscuringTransformer
         implements Transformer, ResourceUtilizer, Session.ResourceKey<ObscuringTransformer.Context> {
      @Override
      public void transform(Session session, ByteBuf in, int offset, int length, boolean lastFragment, ByteBuf out) {
         Context ctx = session.getResource(this);
         if (ctx.firstFragment) {
            out.writeByte('"');
            ctx.firstFragment = false;
         }
         for (int i = 0; i < length; ++i) {
            out.writeByte('x');
         }
         if (lastFragment) {
            out.writeByte('"');
            ctx.firstFragment = true;
         }
      }

      @Override
      public void reserve(Session session) {
         session.declareResource(this, Context::new);
      }

      public static class Context implements Session.Resource {
         private boolean firstFragment = true;
      }
   }
}
