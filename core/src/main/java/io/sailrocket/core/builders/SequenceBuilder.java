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

import java.util.Objects;

import io.sailrocket.api.config.Sequence;
import io.sailrocket.api.config.Step;
import io.sailrocket.core.session.SequenceImpl;

/**
 * @author <a href="mailto:stalep@gmail.com">Ståle Pedersen</a>
 */
public class SequenceBuilder extends BaseSequenceBuilder {
    private final ScenarioBuilder scenario;
    private final String name;
    private int id;
    private Sequence sequence;

    SequenceBuilder(ScenarioBuilder scenario, String name) {
        super(null);
        this.scenario = scenario;
        this.name = Objects.requireNonNull(name);
    }

    public Sequence build() {
        if (sequence != null) {
            return sequence;
        }
        String phase = endSequence().endScenario().name;
        return sequence = new SequenceImpl(phase, this.name, id, steps.stream().flatMap(builder -> builder.build().stream()).toArray(Step[]::new));
    }

    void id(int id) {
        this.id = id;
    }

    public SequenceBuilder sla(SLABuilder sla) {
        endSequence().endScenario().endPhase().endSimulation().addSLA(sla);
        return this;
    }

    public SLABuilder sla() {
        return new SLABuilder(this);
    }

    @Override
    public SequenceBuilder end() {
        return this;
    }

    public ScenarioBuilder endSequence() {
        return scenario;
    }

    public String name() {
        return name;
    }
}
