package io.hyperfoil.core.handlers.json;

import java.io.Serializable;
import java.util.Arrays;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This serves as an abstraction over several {@link ByteStream bytestreams} so that users can just append
 * new buffers to the back and use absolute positioning with single index.
 */
public class StreamQueue {
   protected static final Logger log = LogManager.getLogger(StreamQueue.class);

   private final ByteStream[] parts;
   // indices used by users for the beginning (readerIndex) of the bytestream
   private final int[] userIndex;
   private final int mask;
   private int end = -1;
   private int tail = 0;
   private int length = 0;

   public StreamQueue(int capacity) {
      this.mask = (1 << 32 - Integer.numberOfLeadingZeros(capacity - 1)) - 1;
      this.parts = new ByteStream[capacity];
      this.userIndex = new int[capacity];
      Arrays.fill(userIndex, -1);
   }

   public int append(ByteStream stream) {
      if (parts[tail] != null) {
         log.warn("Too many buffered fragments, dropping data.");
         parts[tail].release();
      }
      ByteStream retained = stream.retain();
      parts[tail] = retained;
      userIndex[tail] = length;
      length += retained.writerIndex() - retained.readerIndex();
      end = tail;
      tail = (tail + 1) & mask;
      return userIndex[end];
   }

   public void release(int index) {
      int i = end;
      while (index < userIndex[i]) {
         i = (i + mask) & mask;
         if (i == end || parts[i] == null) {
            return;
         }
      }
      i = (i + mask) & mask;
      if (i == end || parts[i] == null) {
         return;
      }
      while (i != end && parts[i] != null) {
         userIndex[i] = -1;
         parts[i].release();
         parts[i] = null;
         i = (i + mask) & mask;
      }
   }

   public int getByte(int index) {
      int i = end;
      while (index < userIndex[i]) {
         i = (i + mask) & mask;
         if (i == end || parts[i] == null) {
            //            throw new IndexOutOfBoundsException("Underflowing the queue with index " + index);
            return -1;
         }
      }
      ByteStream part = parts[i];
      int partIndex = index - userIndex[i];
      if (part.readerIndex() + partIndex >= part.writerIndex()) {
         return -1;
      }
      return part.getByte(part.readerIndex() + partIndex);
   }

   public void reset() {
      for (int i = 0; i < parts.length; ++i) {
         if (parts[i] != null) {
            parts[i].release();
            parts[i] = null;
         }
         userIndex[i] = -1;
      }
      length = 0;
      end = -1;
   }

   public <P1, P2> void consume(int startIndex, int endIndex, Consumer<P1, P2> consumer, P1 p1, P2 p2, boolean isComplete) {
      int i = end;
      while (startIndex < userIndex[i]) {
         i = (i + mask) & mask;
         if (i == end || parts[i] == null) {
            //            throw new IndexOutOfBoundsException("Underflowing the queue with index " + index);
            ++i;
            break;
         }
      }
      int partIndex = startIndex - userIndex[i];
      boolean isLast = false;
      while (!isLast) {
         ByteStream part = parts[i];
         int end = part.writerIndex();
         if (endIndex <= userIndex[i] + part.writerIndex() - part.readerIndex()) {
            end = endIndex - userIndex[i] + part.readerIndex();
            isLast = true;
         }
         int length = end - partIndex - part.readerIndex();
         if (length > 0) {
            consumer.accept(p1, p2, part, part.readerIndex() + partIndex, length, isComplete && isLast);
         }
         partIndex = 0;
         i = (i + 1) & mask;
      }
   }

   public interface Consumer<P1, P2> extends Serializable {
      void accept(P1 p1, P2 p2, ByteStream stream, int offset, int length, boolean isLastPart);
   }
}
