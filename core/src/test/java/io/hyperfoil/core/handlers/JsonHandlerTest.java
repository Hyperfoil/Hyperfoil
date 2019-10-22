package io.hyperfoil.core.handlers;

import java.nio.charset.StandardCharsets;

import org.junit.Test;

import io.hyperfoil.api.connection.HttpRequest;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.session.SessionFactory;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class JsonHandlerTest {
   private static byte[] ID418 = "418".getBytes(StandardCharsets.UTF_8);
   private static byte[] ID420 = "420".getBytes(StandardCharsets.UTF_8);
   private static byte[] ID450 = "450".getBytes(StandardCharsets.UTF_8);
   private static final byte[] JSON = ("[\n" +
         "      { \"id\" : 418, \"product\" : \"Teapots\", \"units\" : 123 },\n" +
         "      { \"id\" : 420, \"product\" : \"Various herbs\", \"units\" : 321  },\n" +
         "      { \"id\" : 450, \"product\" : \"Magazines\", \"units\": 456 }\n" +
         "    ]").getBytes(StandardCharsets.UTF_8);

   @Test
   public void testFull() {
      ExpectProcessor expect = new ExpectProcessor()
            .expect(-1, 3, true)
            .expect(-1, 3, true)
            .expect(-1, 3, true);
      JsonHandler handler = new JsonHandler(".[].id", expect);
      Session session = SessionFactory.forTesting();

      ByteBuf data = Unpooled.wrappedBuffer(JSON);

      HttpRequest request = new HttpRequest(session);
      handler.reserve(session);
      handler.before(request);
      handler.process(request, data, data.readerIndex(), data.readableBytes(), true);
      handler.after(request);

      expect.validate();
   }

   @Test
   public void testSplit() {
      ExpectProcessor expect = new ExpectProcessor();
      JsonHandler handler = new JsonHandler(".[].id", expect);
      Session session = SessionFactory.forTesting();
      HttpRequest request = new HttpRequest(session);
      handler.reserve(session);

      for (int i = 0; i < JSON.length; ++i) {
         ByteBuf data1 = Unpooled.wrappedBuffer(JSON, 0, i);
         ByteBuf data2 = Unpooled.wrappedBuffer(JSON, i, JSON.length - i);

         for (byte[] string : new byte[][]{ ID418, ID420, ID450 }) {
            if (contains(JSON, 0, i, string) || contains(JSON, i, JSON.length - i, string)) {
               expect.expect(-1, 3, true);
            } else {
               expect.expect(-1, -1, false);
               expect.expect(-1, -1, true);
            }
         }

         handler.before(request);
         handler.process(request, data1, data1.readerIndex(), data1.readableBytes(), false);
         handler.process(request, data2, data2.readerIndex(), data2.readableBytes(), true);
         handler.after(request);

         expect.validate();
      }
   }

   private boolean contains(byte[] data, int offset, int length, byte[] string) {
      OUTER:
      for (int i = 0; i <= length - string.length; ++i) {
         for (int j = 0; j < string.length && i + j < length; ++j) {
            if (string[j] != data[offset + i + j]) {
               continue OUTER;
            }
         }
         return true;
      }
      return false;
   }
}
