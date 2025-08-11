package io.hyperfoil.controller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

class WriterUtil {
   static <T, E extends Throwable> void printInSync(Map<String, List<T>> map, ThrowingBiConsumer<String, T, E> consumer)
         throws E {
      String[] addresses = map.keySet().toArray(new String[0]);
      @SuppressWarnings("unchecked")
      Iterator<T>[] iterators = Stream.of(addresses).map(a -> map.get(a).iterator()).toArray(Iterator[]::new);
      boolean hadNext;
      do {
         hadNext = false;
         for (int i = 0; i < addresses.length; ++i) {
            if (iterators[i].hasNext()) {
               consumer.accept(addresses[i], iterators[i].next());
               hadNext = true;
            }
         }
      } while (hadNext);
   }

   static void createLocalDir(Path dir) throws IOException {
      File dirAsFile = dir.toFile();
      if (!dirAsFile.exists() && !dirAsFile.mkdirs()) {
         throw new IOException("Cannot create directory " + dir);
      }
   }

   static String sanitize(String phase) {
      return phase.replaceAll(File.separator, "_");
   }

   @FunctionalInterface
   interface ThrowingBiConsumer<A, B, E extends Throwable> {
      void accept(A a, B b) throws E;
   }
}
