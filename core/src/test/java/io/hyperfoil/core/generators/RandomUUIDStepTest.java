package io.hyperfoil.core.generators;

import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

import io.hyperfoil.api.config.Locator;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.core.test.TestUtil;

public class RandomUUIDStepTest {

   @Test
   public void testStringGenerator() {

      // builder
      String[] vars = new String[]{ "first", "second", "third", "fourth" };
      for (String varName : vars) {
         RandomUUIDStep.Builder builder = new RandomUUIDStep.Builder();
         builder.init(String.format("%s", varName));

         // session
         Locator.push(TestUtil.locator());
         List<Step> steps = builder.build();
         Access access = SessionFactory.access(varName);
         Locator.pop();
         Session session = SessionFactory.forTesting(new String[]{varName}, new String[0]);

         // assert
         Step step = steps.get(0);

         RandomUUIDStep randomStringStep = (RandomUUIDStep) step;
         randomStringStep.invoke(session);
         String value = access.getObject(session).toString();
         assertTrue(value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
      }
   }
}
