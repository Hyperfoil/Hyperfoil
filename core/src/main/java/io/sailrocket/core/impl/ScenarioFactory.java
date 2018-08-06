/*
 * Copyright 2018 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sailrocket.core.impl;

import io.sailrocket.api.Scenario;
import io.sailrocket.api.Sequence;

import java.util.List;

public class ScenarioFactory {

    public static Scenario buildScenario(List<Sequence> sequences) {

        ScenarioImpl scenario = new ScenarioImpl();

        sequences.forEach(sequence ->
                scenario.sequence(sequence)
        );

        return scenario;
    }
/*
    public static CompletableFuture<SequenceContext> buildSequenceFuture(SequenceImpl sequence, Worker worker) {

        CompletableFuture<SequenceContext> rootFuture = new CompletableFuture().supplyAsync(() ->
                new ClientSessionImpl(sequence.getHttpClientPool(), worker)
        );

        return sequence.getSteps().stream()
                .reduce(rootFuture
                        , (sequenceFuture, step) -> sequenceFuture.thenCompose(sequenceState -> step.asyncExec(sequenceState))
                        , (sequenceFuture, e) -> sequenceFuture
                );
    }*/
}
