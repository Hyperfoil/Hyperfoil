package io.sailrocket.test;

import static io.sailrocket.core.builders.HttpBuilder.httpBuilder;
import static io.sailrocket.core.builders.ScenarioBuilder.scenarioBuilder;
import static io.sailrocket.core.builders.SequenceBuilder.sequenceBuilder;
import static io.sailrocket.core.builders.SimulationBuilder.simulationBuilder;

import io.sailrocket.api.HttpMethod;
import io.sailrocket.api.Simulation;

public class TestBenchmarks {
   public static Simulation testSimulation() {
      return simulationBuilder()
            .http(httpBuilder().baseUrl("http://localhost:8080"))
            .concurrency(10)
            .connections(10)
            .addPhase("foo").always(1)
               .duration("5s")
               .scenario(scenarioBuilder()
                  .initialSequence(sequenceBuilder()
                        .step().httpRequest(HttpMethod.GET)
                        .path("foo")
                        .endStep()
                        .step().awaitAllResponses()
                        .end()
                  )
            )
            .endPhase()
            .build();
   }
}
