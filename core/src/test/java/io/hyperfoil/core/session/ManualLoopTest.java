package io.hyperfoil.core.session;

import static io.hyperfoil.core.builders.StepCatalog.SC;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.hyperfoil.core.steps.SetIntAction;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class ManualLoopTest extends BaseScenarioTest {
   @Test
   public void test() {
      scenario()
            .initialSequence("test")
            .step(SC).breakSequence()
            .condition().intCondition().fromVar("counter").equalTo().value(1).end().end()
            .endStep()
            .step(SC).action(new SetIntAction.Builder().var("counter").value(1))
            .step(SC).nextSequence("test");

      runScenario();
   }
}
