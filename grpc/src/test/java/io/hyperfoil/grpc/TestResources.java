package io.hyperfoil.grpc;

import java.io.File;
import java.util.Objects;

public class TestResources {

   public enum ProtoFiles {
      HelloWorld("scenarios/helloworld.proto");

      private final File file;

      ProtoFiles(String resourceName) {
         file = getResourceAsFile(resourceName);
      }

      public File file() {
         return file;
      }
   }

   private static File getResourceAsFile(String resourceName) {
      ClassLoader classLoader = TestResources.class.getClassLoader();
      return new File(Objects.requireNonNull(classLoader.getResource(resourceName)).getFile());
   }
}
