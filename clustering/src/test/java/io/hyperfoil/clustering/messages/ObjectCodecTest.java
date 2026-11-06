package io.hyperfoil.clustering.messages;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.vertx.core.buffer.Buffer;

/**
 * Tests the codec. We test with non-empty buffers because Vert.x writes
 * headers into the buffer before our payload.
 */
public class ObjectCodecTest {
   private final ObjectCodec<Object> codec = new ObjectCodec<>();

   private Object roundTrip(Object object, int leadingBytes) {
      Buffer buffer = Buffer.buffer();
      for (int i = 0; i < leadingBytes; ++i) {
         buffer.appendByte((byte) 0xAB);
      }
      codec.encodeToWire(buffer, object);
      return codec.decodeFromWire(leadingBytes, buffer);
   }

   @Test
   public void shouldRoundTripFromTheStartOfTheBuffer() {
      assertEquals("hello", roundTrip("hello", 0));
   }

   @Test
   public void shouldRoundTripAfterExistingContent() {
      assertEquals("hello", roundTrip("hello", 7));
   }

   @Test
   public void shouldNotDisturbWhatWasAlreadyInTheBuffer() {
      Buffer buffer = Buffer.buffer().appendByte((byte) 0xAB).appendByte((byte) 0xCD);

      codec.encodeToWire(buffer, "hello");

      assertEquals((byte) 0xAB, buffer.getByte(0));
      assertEquals((byte) 0xCD, buffer.getByte(1));
   }

   @Test
   public void shouldWriteTheLengthOfExactlyWhatFollows() {
      Buffer buffer = Buffer.buffer();

      codec.encodeToWire(buffer, "hello");

      assertEquals(buffer.length() - Integer.BYTES, buffer.getInt(0), "length prefix must cover the payload");
   }

   /**
    * Tests a large payload to ensure buffer resizing works correctly.
    */
   @Test
   public void shouldRoundTripAPayloadLargerThanTheInitialBuffer() {
      List<String> big = new ArrayList<>();
      for (int i = 0; i < 5000; ++i) {
         big.add("entry-" + i);
      }

      assertEquals(big, roundTrip(big, 3));
   }

   /**
    * A partially written object must not reach the wire as a well-formed frame: the send has to fail where the
    * unserializable object is, not later at the receiver.
    */
   @Test
   public void shouldFailTheSendRatherThanFrameATruncatedObject() {
      Buffer buffer = Buffer.buffer();

      assertThrows(UncheckedIOException.class, () -> codec.encodeToWire(buffer, new Object()));
      assertEquals(0, buffer.getInt(0), "a failed encode must not stamp a length over the partial payload");
   }

   @Test
   public void shouldLeaveThePartialWriteForTheCallerToDiscard() {
      Buffer buffer = Buffer.buffer();

      assertThrows(UncheckedIOException.class, () -> codec.encodeToWire(buffer, new Object()));
      assertTrue(buffer.length() > Integer.BYTES, "the failed encode is not rolled back");
   }

   @Test
   public void shouldRoundTripBackToBackPayloadsInOneBuffer() {
      Buffer buffer = Buffer.buffer();

      codec.encodeToWire(buffer, "first");
      int second = buffer.length();
      codec.encodeToWire(buffer, "second");

      assertEquals("first", codec.decodeFromWire(0, buffer));
      assertEquals("second", codec.decodeFromWire(second, buffer));
   }
}
