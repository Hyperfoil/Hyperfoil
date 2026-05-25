package io.hyperfoil.api.config;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

public interface BenchmarkData {
   BenchmarkData EMPTY = new BenchmarkData() {
      @Override
      public InputStream readFile(String file) {
         throw new MissingFileException(file, "Cannot load file " + file + " (file set is empty).", null);
      }

      @Override
      public Map<String, byte[]> files() {
         return Collections.emptyMap();
      }
   };

   static String sanitize(String file) {
      return file.replace(File.separatorChar, '_').replace(File.pathSeparatorChar, '_');
   }

   default String readFileAsString(String file, Charset charset) {
      try (InputStream in = readFile(file)) {
         if (in == null) {
            return null;
         }
         byte[] readBytes = new byte[in.available()];
         int remaining = readBytes.length;
         while (in.available() > 0) {
            if (remaining == 0) {
               readBytes = Arrays.copyOf(readBytes, readBytes.length + in.available());
               remaining = readBytes.length;
            }
            int read = in.read(readBytes, readBytes.length - remaining, remaining);
            if (read < 0) {
               break;
            }
            remaining -= read;
         }
         return new String(readBytes, 0, readBytes.length - remaining, charset);
      } catch (Exception e) {
         throw new MissingFileException(file, "Cannot load file " + file, e);
      }
   }

   InputStream readFile(String file);

   Map<String, byte[]> files();

   class MissingFileException extends RuntimeException {
      public final String file;

      public MissingFileException(String file) {
         this.file = file;
      }

      public MissingFileException(String file, String message, Throwable cause) {
         super(message, cause);
         this.file = file;
      }
   }
}
