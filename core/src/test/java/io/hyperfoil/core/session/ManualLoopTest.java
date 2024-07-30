package io.hyperfoil.core.session;

import static io.hyperfoil.core.builders.StepCatalog.SC;

import org.junit.jupiter.api.Test;

import io.hyperfoil.core.steps.RestartSequenceAction;
import io.hyperfoil.core.steps.SetIntAction;

public class ManualLoopTest extends BaseScenarioTest {
   @Test
   public void test() {
      scenario()
            .initialSequence("test")
            .step(SC).breakSequence()
            .condition().intCondition().fromVar("counter").equalTo().value(1).end().end()
            .endStep()
            .step(SC).action(new SetIntAction.Builder().var("counter").value(1))
            .step(SC).action(new RestartSequenceAction.Builder());

      runScenario();
   }
}
