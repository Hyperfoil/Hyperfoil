package io.hyperfoil.core.generators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import io.hyperfoil.api.config.Locator;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.session.WriteAccess;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.core.test.TestUtil;

public class RandomCsvRowStepTest {

   private static final String[][] DATA = new String[][]{
         { "one", "two two", "three, three", "four\"four" },
         { "     five", "six ", "", "eight" },
         { "nine", "", "eleven", "twelve\n ends here" }
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
      Locator.push(TestUtil.locator());
      RandomCsvRowStep.Builder builder = new RandomCsvRowStep.Builder()
            .skipComments(true)
            .file("data/testdata.csv");
      for (int i = 0; i < vars.length; ++i) {
         if (vars[i] != null) {
            builder.columns().accept(String.valueOf(i), vars[i]);
         }
      }

      WriteAccess[] access = Arrays.stream(vars).map(SessionFactory::objectAccess).toArray(WriteAccess[]::new);
      Session session = SessionFactory.forTesting(access);
      List<Step> steps = builder.build();
      Locator.pop();
      RandomCsvRowStep csvRowStep = (RandomCsvRowStep) steps.get(0);
      TestUtil.resolveAccess(session, csvRowStep);

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
