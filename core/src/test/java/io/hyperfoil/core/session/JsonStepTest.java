package io.hyperfoil.core.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.hyperfoil.api.session.ObjectAccess;
import io.hyperfoil.api.session.ReadAccess;
import io.hyperfoil.core.data.DataFormat;
import io.hyperfoil.core.steps.JsonStep;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class JsonStepTest extends BaseScenarioTest {
   @Test
   public void test() {
      scenario()
            .initialSequence("test")
            .step(() -> {
               ObjectAccess json = SessionFactory.objectAccess("json");
               return s1 -> {
                  json.setObject(s1, "{ \"foo\" : \"bar\\nbar\" }".getBytes(StandardCharsets.UTF_8));
                  return true;
               };
            })
            .stepBuilder(new JsonStep.Builder()
                  .fromVar("json")
                  .query(".foo")
                  .toVar("output")
                  .format(DataFormat.STRING))
            .step(() -> {
               ReadAccess output = SessionFactory.readAccess("output");
               return s -> {
                  assertThat(output.getObject(s)).isEqualTo("bar\nbar");
                  return true;
               };
            });
      runScenario();
   }
}
