package io.hyperfoil.core.handlers.json;

import java.io.Serializable;
import java.util.Arrays;

import org.jctools.util.Pow2;

/**
 * This serves as an abstraction over several {@link ByteStream bytestreams} so that users can just append
 * new buffers to the back and use absolute positioning with single index.
 */
public class StreamQueue {
   private ByteStream[] parts;
   // indices used by users for the beginning (readerIndex) of the bytestream
   private int[] userIndex;
   private int mask;
   private int end = -1;
   private int length = 0;

   public StreamQueue(int initialCapacity) {
      initialCapacity = Pow2.roundToPowerOfTwo(initialCapacity);
      this.mask = initialCapacity - 1;
      this.parts = new ByteStream[initialCapacity];
      this.userIndex = new int[initialCapacity];
      Arrays.fill(userIndex, -1);
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
            bytes += readableBytesOf(part);
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
         int next = next(end);
         int contiguousNulls = 0;
         for (int i = 0; i < parts.length; i++) {
            if (parts[next] != null) {
               break;
            }
            next = next(next);
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

   private int totalAppendedBytes() {
      return length;
   }

   public int enlargeCapacity(int tail) {
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
      return tail;
   }

   public int append(ByteStream stream) {
      int tail = next(end);
      if (parts[tail] != null) {
         // enlarging capacity can modify end - we need an updated tail
         tail = enlargeCapacity(tail);
      }
      ByteStream retained = stream.retain();
      parts[tail] = retained;
      int newUserIndex = length;
      userIndex[tail] = newUserIndex;
      length += readableBytesOf(retained);
      end = tail;
      return newUserIndex;
   }

   public void releaseUntil(int index) {
      int i = findPartIndexWith(index);
      if (i < 0) {
         return;
      }
      assert end >= 0;
      // Even if index is the last byte accessible within parts[i] we cannot release it too :"(
      // but we can release the rest
      i = prev(i);
      while (hasMoreParts(i)) {
         releasePart(i);
         i = prev(i);
      }
   }

   public int getByte(int index) {
      int i = findPartIndexWith(index);
      if (i < 0) {
         return -1;
      }
      ByteStream part = parts[i];
      int partIndex = partOffset(index, i);
      if (partIndex >= readableBytesOf(part)) {
         return -1;
      }
      return part.getByte(readerIndexOf(part, partIndex));
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
      if (i < 0) {
         return -1;
      }
      while (index < userIndex[i]) {
         i = prev(i);
         if (!hasMoreParts(i)) {
            return -1;
         }
      }
      return i;
   }

   public void reset() {
      if (end >= 0) {
         int i = end;
         do {
            releasePart(i);
            i = prev(i);
         } while (hasMoreParts(i));
         end = -1;
      }
      length = 0;
   }

   public <P1, P2> void consume(int startIndex, int endIndex, Consumer<P1, P2> consumer, P1 p1, P2 p2, boolean isComplete) {
      validateIndexes(startIndex, endIndex);
      if (startIndex == endIndex) {
         return;
      }
      int i = findPartIndexWith(startIndex);
      if (i < 0) {
         throw new IllegalArgumentException("Start index " + startIndex + " not found.");
      }
      int partStartIndex = partOffset(startIndex, i);
      boolean isLast = false;
      while (!isLast) {
         ByteStream part = parts[i];
         final int readableBytes = readableBytesOf(part);
         final int partEndIndex = partOffset(endIndex, i);
         isLast = partEndIndex <= readableBytes;
         final int length;
         if (isLast) {
            length = partEndIndex - partStartIndex;
         } else {
            length = readableBytes - partStartIndex;
         }
         if (length > 0) {
            consumer.accept(p1, p2, part, readerIndexOf(part, partStartIndex), length, isComplete && isLast);
         }
         partStartIndex = 0;
         i = next(i);
      }
   }

   private static int readableBytesOf(ByteStream stream) {
      return stream.writerIndex() - stream.readerIndex();
   }

   private static int readerIndexOf(ByteStream part, int partIndex) {
      return part.readerIndex() + partIndex;
   }

   private int partOffset(int streamIndex, int part) {
      return streamIndex - userIndex[part];
   }

   private void validateIndexes(int startIndex, int endIndex) {
      if (startIndex < 0 || endIndex < 0) {
         throw new IllegalArgumentException("Start and end indexes must be non-negative.");
      }
      if (startIndex >= length || endIndex > length) {
         throw new IllegalArgumentException("Start and end indexes must be within the bounds of the stream.");
      }
      if (startIndex > endIndex) {
         throw new IllegalArgumentException("Start index must be less than end index.");
      }
   }

   private int prev(int i) {
      return (i - 1) & mask;
   }

   private int next(int i) {
      return (i + 1) & mask;
   }

   private boolean hasMoreParts(int i) {
      return i != end && userIndex[i] != -1;
   }

   private void releasePart(int i) {
      userIndex[i] = -1;
      parts[i].release();
      parts[i] = null;
   }

   public interface Consumer<P1, P2> extends Serializable {
      void accept(P1 p1, P2 p2, ByteStream stream, int offset, int length, boolean isLastPart);
   }
}
