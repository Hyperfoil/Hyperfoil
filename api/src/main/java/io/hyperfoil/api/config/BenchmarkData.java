package io.hyperfoil.api.config;

import java.io.File;
import java.io.InputStream;
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
