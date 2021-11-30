package io.hyperfoil.core.test;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.api.config.BenchmarkData;
import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.Locator;
import io.hyperfoil.api.session.AccessVisitor;
import io.hyperfoil.api.session.ReadAccess;
import io.hyperfoil.api.session.Session;

public class TestUtil {
   private static final BenchmarkData TESTING_DATA = new BenchmarkData() {
      @Override
      public InputStream readFile(String file) {
         InputStream stream = getClass().getClassLoader().getResourceAsStream(file);
         if (stream == null) {
            throw new BenchmarkDefinitionException("Cannot load file " + file + " from current classloader.");
         }
         return stream;
      }

      @Override
      public Map<String, byte[]> files() {
         return Collections.emptyMap();
      }
   };

   private static final Locator TESTING_MOCK = new Locator.Abstract() {

      @Override
      public BenchmarkBuilder benchmark() {
         return new BenchmarkBuilder(null, TESTING_DATA);
      }

      @Override
      public String locationMessage() {
         throw new UnsupportedOperationException();
      }
   };

   public static String randomString(ThreadLocalRandom rand, int maxLength) {
      int length = rand.nextInt(maxLength);
      char[] chars = new char[length];
      for (int i = 0; i < length; ++i) {
         chars[i] = (char) rand.nextInt('a', 'z' + 1);
      }
      return String.valueOf(chars);
   }

   public static Locator locator() {
      return TESTING_MOCK;
   }

   public static BenchmarkData benchmarkData() {
      return TESTING_DATA;
   }

   public static void resolveAccess(Session session, Object object) {
      AccessVisitor scenarioVisitor = new AccessVisitor();
      scenarioVisitor.visit(session.phase().definition().scenario);
      Map<Object, Integer> indices = Arrays.stream(scenarioVisitor.reads())
            .collect(Collectors.toMap(ReadAccess::key, ReadAccess::index));

      AccessVisitor objectVisitor = new AccessVisitor();
      objectVisitor.visit(object);
      for (ReadAccess access : objectVisitor.reads()) {
         access.setIndex(indices.get(access.key()));
      }
   }
}
