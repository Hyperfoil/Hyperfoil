package io.hyperfoil.core.handlers.json;

import java.io.Serializable;
import java.util.Arrays;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jctools.util.Pow2;

/**
 * This serves as an abstraction over several {@link ByteStream bytestreams} so that users can just append
 * new buffers to the back and use absolute positioning with single index.
 */
public class StreamQueue {
   protected static final Logger log = LogManager.getLogger(StreamQueue.class);

   private ByteStream[] parts;
   // indices used by users for the beginning (readerIndex) of the bytestream
   private int[] userIndex;
   private int mask;
   private int end = -1;
   private int tail = 0;
   private int length = 0;

   public StreamQueue(int initialCapacity) {
      initialCapacity = Pow2.roundToPowerOfTwo(initialCapacity);
      this.mask = initialCapacity - 1;
      this.parts = new ByteStream[initialCapacity];
      this.userIndex = new int[initialCapacity];
      Arrays.fill(userIndex, -1);
   }

   int firstIndex() {
      if (end < 0) {
         return -1;
      }
      var lastPart = parts[end];
      int lastPartSize = lastPart.writerIndex() - lastPart.readerIndex();
      int totalIndexes = userIndex[end] + lastPartSize - length;
      return totalIndexes;
   }

   /**
    * WARNING: use this only for testing!
    */
   int firstAvailableIndex() {
      return totalAppendedBytes() - bytes();
   }

   /**
    * WARNING: use this only for testing!
    */
   int bytes() {
      int bytes = 0;
      for (ByteStream part : this.parts) {
         if (part != null) {
            bytes += (part.writerIndex() - part.readerIndex());
         }
      }
      return bytes;
   }

   /**
    * WARNING: use this only for testing!
    */
   int availableCapacityBeforeEnlargement() {
      // how many nulls from end to tail?
      if (end == -1) {
         return parts.length;
      } else {
         // how many nulls, including the tail we have till wrapping back to end?
         int next = tail;
         int contiguousNulls = 0;
         for (int i = 0; i < parts.length; i++) {
            if (parts[next] != null) {
               break;
            }
            next = (next + 1) & mask;
            contiguousNulls++;
         }
         return contiguousNulls;
      }
   }

   /**
    * WARNING: use this only for testing!
    */
   int parts() {
      int count = 0;
      for (ByteStream part : this.parts) {
         if (part != null) {
            count++;
         }
      }
      return count;
   }

   /**
    * WARNING: use this only for testing!
    */
   int totalAppendedBytes() {
      return length;
   }

   public void enlargeCapacity() {
      assert parts[tail] != null;
      int newCapacity = Pow2.roundToPowerOfTwo(mask + 2);
      ByteStream[] newParts = new ByteStream[newCapacity];
      int[] newUserIndex = new int[newCapacity];
      int secondHalfToCopy = parts.length - tail;
      System.arraycopy(parts, tail, newParts, 0, secondHalfToCopy);
      System.arraycopy(userIndex, tail, newUserIndex, 0, secondHalfToCopy);
      if (tail > 0) {
         // copy the first part only if needed
         System.arraycopy(parts, 0, newParts, secondHalfToCopy, tail);
         System.arraycopy(userIndex, 0, newUserIndex, secondHalfToCopy, tail);
      }
      // fill the rest with -1
      Arrays.fill(newUserIndex, userIndex.length, newCapacity, -1);
      mask = newCapacity - 1;
      end = parts.length - 1;
      tail = parts.length;
      userIndex = newUserIndex;
      // help GC
      Arrays.fill(parts, null);
      parts = newParts;
      assert parts[tail] == null;
   }

   public int append(ByteStream stream) {
      if (parts[tail] != null) {
         enlargeCapacity();
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
      int i = findPartIndexWith(index);
      if (i < 0) {
         return;
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
      int i = findPartIndexWith(index);
      if (i < 0) {
         return -1;
      }
      ByteStream part = parts[i];
      int partIndex = index - userIndex[i];
      if (part.readerIndex() + partIndex >= part.writerIndex()) {
         return -1;
      }
      return part.getByte(part.readerIndex() + partIndex);
   }

   /**
    * Since parts are ordered based on the starting index they deal with, this search backward from the last appended
    * one - which contains the highest indexes - to find the part which contains the given index.
    */
   private int findPartIndexWith(int index) {
      if (index < 0) {
         return -1;
      }
      int i = end;
      while (index < userIndex[i]) {
         // move backwards while dealing with the wrap-around
         i = (i + mask) & mask;
         if (i == end || parts[i] == null) {
            return -1;
         }
      }
      return i;
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
