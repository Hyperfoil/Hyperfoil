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

   private static void trackReleaseUntil(ByteStream stream) {
      releaseCounters.computeIfAbsent(stream, s -> new AtomicInteger()).incrementAndGet();
   }

   /**
    * WARNING: this assumes that data generation and order by which data is appended is the same!
    */
   private static byte[] generateData(int length) {
      byte[] array = new byte[length];
      int value = generatedDataBytes;
      for (int i = 0; i < length; ++i) {
         int nextValue = value + i;
         byte positiveValue = (byte) (nextValue & 0x7F);
         assert positiveValue >= 0;
         array[i] = positiveValue;
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
      Consumer<ByteStream> release = StreamQueueTest::trackReleaseUntil;
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
      assertEquals(DATA_SIZE, streamQueue.bytes());
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
      assertEquals(DATA_SIZE * 2, streamQueue.bytes());
      assertArrayEquals(dumpGeneratedData(), streamDataOf(streamQueue));
      streamQueue.append(streamFactory.get());
      assertEquals(1, streamQueue.availableCapacityBeforeEnlargement());
      assertEquals(3, streamQueue.parts());
      assertEquals(DATA_SIZE * 3, streamQueue.bytes());
      assertArrayEquals(dumpGeneratedData(), streamDataOf(streamQueue));
   }

   @ParameterizedTest
   @MethodSource("byteStreamProvider")
   public void appendAndRemoveWithEnlargementShouldContainsTheRightData(Supplier<ByteStream> streamFactory) {
      var streamQueue = new StreamQueue(1);
      streamQueue.append(streamFactory.get());
      streamQueue.append(streamFactory.get());
      assertEquals(0, streamQueue.availableCapacityBeforeEnlargement());
      streamQueue.releaseUntil(DATA_SIZE);
      assertEquals(1, streamQueue.availableCapacityBeforeEnlargement());
      removeFirstGeneratedData();
      streamQueue.append(streamFactory.get());
      assertEquals(0, streamQueue.availableCapacityBeforeEnlargement());
      assertArrayEquals(dumpGeneratedData(), streamDataOf(streamQueue));
   }

   @ParameterizedTest
   @MethodSource("byteStreamProvider")
   public void removeIfEmptyShouldNotThrowErrors(Supplier<ByteStream> streamFactory) {
      var streamQueue = new StreamQueue(1);
      streamQueue.releaseUntil(1);
   }

   @ParameterizedTest
   @MethodSource("byteStreamProvider")
   public void removeBeyondExistingIndexesShouldNotRemoveLastAppended(Supplier<ByteStream> streamFactory) {
      var streamQueue = new StreamQueue(2);
      var firstPart = streamFactory.get();
      streamQueue.append(firstPart);
      var secondPart = streamFactory.get();
      streamQueue.append(secondPart);
      int beyondLastIndex = streamQueue.bytes();
      assertEquals(-1, streamQueue.getByte(beyondLastIndex));
      streamQueue.releaseUntil(3 * DATA_SIZE);
      assertRelease(firstPart, 1);
      assertRelease(secondPart, 0);
      assertEquals(1, streamQueue.parts());
      removeFirstGeneratedData();
      assertArrayEquals(dumpGeneratedData(), streamDataOf(streamQueue));
   }

   @ParameterizedTest
   @MethodSource("byteStreamProvider")
   public void removeLastByteOfPartShouldNotRemoveIt(Supplier<ByteStream> streamFactory) {
      var streamQueue = new StreamQueue(2);
      var firstPart = streamFactory.get();
      streamQueue.append(firstPart);
      var secondPart = streamFactory.get();
      streamQueue.append(secondPart);
      streamQueue.releaseUntil(2 * DATA_SIZE);
      assertRelease(firstPart, 1);
      assertRelease(secondPart, 0);
      assertEquals(1, streamQueue.parts());
      removeFirstGeneratedData();
      assertArrayEquals(dumpGeneratedData(), streamDataOf(streamQueue));
   }

   @ParameterizedTest
   @MethodSource("byteStreamProvider")
   public void appendShouldReturnTheFirstIndexOfEachPart(Supplier<ByteStream> streamFactory) {
      var streamQueue = new StreamQueue(2);
      var part = streamFactory.get();
      int index = streamQueue.append(part);
      assertEquals(0, index);
      index = streamQueue.append(streamFactory.get());
      assertEquals(index, DATA_SIZE);
   }

   @ParameterizedTest
   @MethodSource("byteStreamProvider")
   public void resetShouldReleaseAllPartsAndResetLength(Supplier<ByteStream> streamFactory) {
      var streamQueue = new StreamQueue(2);
      final var part = streamFactory.get();
      streamQueue.append(part);
      streamQueue.reset();
      assertArrayEquals(new byte[0], streamDataOf(streamQueue));
      assertRelease(part, 1);
      assertEquals(0, streamQueue.append(streamFactory.get()));
   }
}
