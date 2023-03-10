package io.hyperfoil.core.generators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
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
   public void testParseAllColumns() {
      Locator.push(TestUtil.locator());
      RandomCsvRowStep.Builder builder = new RandomCsvRowStep.Builder()
            .skipComments(true)
            .file("data/testdata.csv");
      var cols = builder.columns();
      for (int i = 0; i < 4; i++) {
         var pos = Integer.toString(i);
         cols.accept(pos, pos);
      }
      String[][] rows = ((RandomCsvRowStep) builder.build().get(0)).rows();
      Assert.assertEquals(rows.length, DATA.length);
      for (int i = 0; i < DATA.length; i++) {
         Assert.assertArrayEquals(DATA[i], rows[i]);
      }
   }

   @Test
   public void testSelectDifferentColumns() {
      Locator.push(TestUtil.locator());
      class MutableInt {
         int i = 0;
      }

      var mutableInt = new MutableInt();
      RandomCsvRowStep.Builder builder = new RandomCsvRowStep.Builder()
            .skipComments(true)
            .customSelector(limit -> mutableInt.i++)
            .file("data/testdata.csv");
      var cols = builder.columns();
      var vars = new String[4];
      for (int i = 0; i < 4; i++) {
         var pos = Integer.toString(i);
         vars[i] = pos;
         cols.accept(pos, pos);
      }
      var access = Arrays.stream(vars).map(SessionFactory::objectAccess).toArray(WriteAccess[]::new);
      var session = SessionFactory.forTesting(access);
      var steps = builder.build();
      Locator.pop();
      var csvRowStep = (RandomCsvRowStep) steps.get(0);
      TestUtil.resolveAccess(session, csvRowStep);
      for (String[] row : DATA) {
         Assert.assertTrue(csvRowStep.invoke(session));
         Assert.assertEquals(row.length, access.length);
         for (int i = 0; i < row.length; i++) {
            assertThat(access[i].getObject(session)).isEqualTo(row[i]);
         }
      }
   }

   @Test
   public void testAllColumns() {
      test(new String[]{ "first", "second", "third", "fourth" });
   }

   @Test
   public void testTwoColums() {
      test(new String[]{ "first", null, "third", null });
   }

   @Test
   public void serializeAndDeserializeStepIgnoreCustomSelector() throws IOException, ClassNotFoundException {
      Locator.push(TestUtil.locator());
      RandomCsvRowStep.Builder builder = new RandomCsvRowStep.Builder()
            .skipComments(true)
            .customSelector(limit -> 0)
            .file("data/testdata.csv");
      var cols = builder.columns();
      var vars = new String[4];
      for (int i = 0; i < 4; i++) {
         var pos = Integer.toString(i);
         vars[i] = pos;
         cols.accept(pos, pos);
      }
      var steps = builder.build();
      Locator.pop();
      var step = steps.get(0);
      final byte[] serializedBytes;
      try (var byteArrayStream = new ByteArrayOutputStream();
           var objectOutputStream = new ObjectOutputStream(byteArrayStream)) {
         objectOutputStream.writeObject(step);
         objectOutputStream.flush();
         serializedBytes = byteArrayStream.toByteArray();
      }
      try (var objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serializedBytes))) {
         var deserializedStep = (Step) objectInputStream.readObject();
         assertThat(deserializedStep).isInstanceOf(step.getClass());
         assertThat(((RandomCsvRowStep) deserializedStep).rowSelector()).isNull();
      }
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
