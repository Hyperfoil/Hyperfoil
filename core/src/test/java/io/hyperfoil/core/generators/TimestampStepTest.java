package io.hyperfoil.core.generators;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.hyperfoil.api.config.Locator;
import io.hyperfoil.api.session.ObjectAccess;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.core.steps.TimestampStep;
import io.hyperfoil.core.test.TestUtil;

public class TimestampStepTest {
   @Test
   public void testTimestamp() {
      Locator.push(TestUtil.locator());
      try {
         TimestampStep step = (TimestampStep) new TimestampStep.Builder().toVar("foo").pattern("yyyyy.MMMMM.dd GGG hh:mm aaa")
               .build().get(0);
         ObjectAccess foo = SessionFactory.objectAccess("foo");
         Session session = SessionFactory.forTesting(foo);
         step.reserve(session);
         TestUtil.resolveAccess(session, step);

         assertThat(step.invoke(session)).isTrue();
         assertThat(foo.getObject(session)).matches(ts -> {
            String timestamp = (String) ts;
            return timestamp.matches("020[0-9][0-9].[a-zA-Z]+.[0-9][0-9] AD [0-9][0-9]:[0-9][0-9] [AP]M");
         });
      } finally {
         Locator.pop();
      }
   }
}
