package io.hyperfoil.core.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.hyperfoil.api.session.Access;
import io.hyperfoil.core.data.DataFormat;
import io.hyperfoil.core.steps.JsonStep;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class JsonStepTest extends BaseScenarioTest {
   @Override
   protected void initRouter() {
   }

   @Test
   public void test() {
      Access json = SessionFactory.access("json");
      Access output = SessionFactory.access("output");
      scenario()
            .objectVar("json")
            .objectVar("output")
            .initialSequence("test")
            .step(s -> {
               json.setObject(s, "{ \"foo\" : \"bar\\nbar\" }".getBytes(StandardCharsets.UTF_8));
               return true;
            })
            .stepBuilder(new JsonStep.Builder()
                  .fromVar("json")
                  .query(".foo")
                  .toVar("output")
                  .format(DataFormat.STRING))
            .step(s -> {
               assertThat(output.getObject(s)).isEqualTo("bar\nbar");
               return true;
            });
      runScenario();
   }
}
