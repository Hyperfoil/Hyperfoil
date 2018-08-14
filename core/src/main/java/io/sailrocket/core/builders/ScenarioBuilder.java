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
 *
 */

package io.sailrocket.core.builders;

import io.sailrocket.api.Sequence;
import io.sailrocket.core.impl.ScenarioImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * @author <a href="mailto:stalep@gmail.com">Ståle Pedersen</a>
 */
public class ScenarioBuilder {

    private List<Sequence> sequences;

    private ScenarioBuilder() {
        sequences = new ArrayList<>();
    }

    public static ScenarioBuilder scenarioBuilder() {
        return new ScenarioBuilder();
    }

    private ScenarioBuilder apply(Consumer<ScenarioBuilder> consumer) {
        consumer.accept(this);
        return this;
    }

    public ScenarioBuilder sequence(Sequence sequence) {
        return apply(clone ->  clone.sequences.add(sequence));
    }

    public ScenarioBuilder sequence(SequenceBuilder sequenceBuilder) {
        return apply(clone ->  clone.sequences.add(sequenceBuilder.build()));
    }

    public ScenarioImpl build() {
        ScenarioImpl scenario = new ScenarioImpl();
        for(Sequence sequence : sequences)
            scenario.sequence(sequence);

        return scenario;
    }

}
