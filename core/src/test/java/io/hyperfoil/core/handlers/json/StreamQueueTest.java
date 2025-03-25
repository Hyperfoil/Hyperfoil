package io.hyperfoil.core.handlers.json;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import io.netty.buffer.Unpooled;

public class StreamQueueTest {

   private static final int DATA_SIZE = 10;
   private static IdentityHashMap<ByteStream, AtomicInteger> releaseCounters = new IdentityHashMap<>();
   private static IdentityHashMap<ByteStream, AtomicInteger> retainCounters = new IdentityHashMap<>();
   private static int generatedDataBytes;
   private static List<byte[]> generatedData = new ArrayList<>();

   @AfterEach
   public void tearDown() {
      releaseCounters.clear();
      retainCounters.clear();
      generatedDataBytes = 0;
      generatedData.clear();
   }

   private static ByteStream trackRetain(ByteStream stream) {
      retainCounters.computeIfAbsent(stream, s -> new AtomicInteger()).incrementAndGet();
      return stream;
   }

   private static void trackRelease(ByteStream stream) {
      releaseCounters.computeIfAbsent(stream, s -> new AtomicInteger()).incrementAndGet();
   }

   /**
    * WARNING: this assumes that data generation and order by which data is appended is the same!
    */
   private static byte[] generateData(int length) {
      byte[] array = new byte[length];
      int value = generatedDataBytes;
      for (int i = 0; i < length; ++i) {
         array[i] = (byte) value++;
      }
      generatedDataBytes += length;
      generatedData.add(array);
      return array;
   }

   private static byte[] dumpGeneratedData() {
      byte[] allData = new byte[generatedDataBytes];
      int written = 0;
      for (byte[] array : generatedData) {
         System.arraycopy(array, 0, allData, written, array.length);
         written += array.length;
      }
      return allData;
   }

   private static void removeFirstGeneratedData() {
      if (generatedData.isEmpty()) {
         return;
      }
      var data = generatedData.remove(0);
      generatedDataBytes -= data.length;
   }

   private static void removeLastGeneratedData() {
      if (generatedData.isEmpty()) {
         return;
      }
      var lastData = generatedData.remove(generatedData.size() - 1);
      generatedDataBytes -= lastData.length;
   }

   static Supplier<ByteStream>[] byteStreamProvider() {
      Function<ByteStream, ByteStream> retain = StreamQueueTest::trackRetain;
      Consumer<ByteStream> release = StreamQueueTest::trackRelease;
      return new Supplier[] {
            () -> new ByteBufByteStream(retain, release).wrap(Unpooled.wrappedBuffer(generateData(DATA_SIZE)), 0, 10),
            () -> new ByteArrayByteStream(retain, release).wrap(generateData(DATA_SIZE))
      };
   }

   private static void assertRetain(ByteStream stream, int expectedCount) {
      assertCount(stream, expectedCount, retainCounters);
   }

   private static void assertRelease(ByteStream stream, int expectedCount) {
      assertCount(stream, expectedCount, releaseCounters);
   }

   private static void assertCount(ByteStream stream, int expectedCount, Map<ByteStream, AtomicInteger> countMap) {
      var counter = countMap.get(stream);
      if (expectedCount == 0) {
         assertNull(counter);
      } else {
         assertNotNull(counter);
         assertEquals(expectedCount, counter.get());
      }
   }

   private static byte[] streamDataOf(StreamQueue streamQueue) {
      byte[] streamData = new byte[streamQueue.bytes()];
      int offset = streamQueue.firstAvailableIndex();
      for (int i = 0; i < streamData.length; ++i) {
         streamData[i] = (byte) streamQueue.getByte(offset);
         offset++;
      }
      return streamData;
   }

   @ParameterizedTest
   @MethodSource("byteStreamProvider")
   public void appendShouldRetainStream(Supplier<ByteStream> streamFactory) {
      var streamQueue = new StreamQueue(1);
      ByteStream stream = streamFactory.get();
      assertRetain(stream, 0);
      streamQueue.append(stream);
      assertRetain(stream, 1);
   }

   @ParameterizedTest
   @MethodSource("byteStreamProvider")
   public void appendShouldContainsTheOriginalData(Supplier<ByteStream> streamFactory) {
      var streamQueue = new StreamQueue(1);
      ByteStream stream = streamFactory.get();
      assertRetain(stream, 0);
      streamQueue.append(stream);
      assertRetain(stream, 1);
      assertEquals(1, streamQueue.parts());
      assertEquals(DATA_SIZE, streamQueue.totalAppendedBytes());
      assertArrayEquals(dumpGeneratedData(), streamDataOf(streamQueue));
   }

   @ParameterizedTest
   @MethodSource("byteStreamProvider")
   public void appendWithEnlargementShouldContainsTheOriginalData(Supplier<ByteStream> streamFactory) {
      var streamQueue = new StreamQueue(1);
      assertEquals(1, streamQueue.availableCapacityBeforeEnlargement());
      streamQueue.append(streamFactory.get());
      assertEquals(0, streamQueue.availableCapacityBeforeEnlargement());
      streamQueue.append(streamFactory.get());
      assertEquals(0, streamQueue.availableCapacityBeforeEnlargement());
      assertEquals(2, streamQueue.parts());
      assertEquals(DATA_SIZE * 2, streamQueue.totalAppendedBytes());
      assertArrayEquals(dumpGeneratedData(), streamDataOf(streamQueue));
      streamQueue.append(streamFactory.get());
      assertEquals(1, streamQueue.availableCapacityBeforeEnlargement());
      assertEquals(3, streamQueue.parts());
      assertEquals(DATA_SIZE * 3, streamQueue.totalAppendedBytes());
      assertArrayEquals(dumpGeneratedData(), streamDataOf(streamQueue));
   }

   @ParameterizedTest
   @MethodSource("byteStreamProvider")
   public void appendAndRemoveWithEnlargementShouldContainsTheRightData(Supplier<ByteStream> streamFactory) {
      var streamQueue = new StreamQueue(1);
      streamQueue.append(streamFactory.get());
      streamQueue.append(streamFactory.get());
      assertEquals(0, streamQueue.availableCapacityBeforeEnlargement());
      // this should remove enough data to fit another stream
      streamQueue.release(DATA_SIZE);
      assertEquals(1, streamQueue.availableCapacityBeforeEnlargement());
      removeFirstGeneratedData();
      assertArrayEquals(dumpGeneratedData(), streamDataOf(streamQueue));
   }
}