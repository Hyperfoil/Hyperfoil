package io.hyperfoil.core.generators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.util.List;
import java.util.stream.Stream;

import org.junit.Test;

import io.hyperfoil.api.config.Locator;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.core.test.TestUtil;

public class RandomCsvRowStepTest {

   private static final String[][] DATA = new String[][]{
         { "one", "two", "three", "four" },
         { "five", "six", "seven", "eight" },
         { "nine", "ten", "eleven", "twelve" }
   };

   @Test
   public void testAllColumns() {
      test(new String[]{ "first", "second", "third", "fourth" });
   }

   @Test
   public void testTwoColums() {
      test(new String[]{ "first", null, "third", null });
   }

   private void test(String[] vars) {
      RandomCsvRowStep.Builder builder = new RandomCsvRowStep.Builder()
            .skipComments(true)
            .file("src/test/resources/data/testdata.csv");
      for (int i = 0; i < vars.length; ++i) {
         if (vars[i] != null) {
            builder.columns().accept(String.valueOf(i), vars[i]);
         }
      }

      Locator.push(TestUtil.locator());
      List<Step> steps = builder.build();
      Access[] access = Stream.of(vars).map(var -> var != null ? SessionFactory.access(var) : null).toArray(Access[]::new);
      Locator.pop();
      RandomCsvRowStep csvRowStep = (RandomCsvRowStep) steps.get(0);

      Session session = SessionFactory.forTesting(vars, new String[0]);
      OUTER:
      for (int i = 0; i < 10; ++i) {
         csvRowStep.invoke(session);
         Object first = access[0].getObject(session);
         for (String[] row : DATA) {
            if (row[0].equals(first)) {
               for (int j = 1; j < row.length; ++j) {
                  if (access[j] != null) {
                     assertThat(access[j].getObject(session)).isEqualTo(row[j]);
                  }
               }
               continue OUTER;
            }
         }
         fail("No match for row: %s", first);
      }
   }
}
