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
import io.sailrocket.api.Step;
import io.sailrocket.core.session.SequenceImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * @author <a href="mailto:stalep@gmail.com">Ståle Pedersen</a>
 */
public class SequenceBuilder {

    private final String name;
    private List<StepBuilder> steps;
    private int id;
    private Sequence sequence;

    private SequenceBuilder(String name) {
        this.name = name;
        steps = new ArrayList<>();
    }

    public static SequenceBuilder sequenceBuilder() {
        return new SequenceBuilder(null);
    }

    public static SequenceBuilder sequenceBuilder(String name) {
        return new SequenceBuilder(name);
    }

    private SequenceBuilder apply(Consumer<SequenceBuilder> consumer) {
        assert sequence == null;
        consumer.accept(this);
        return this;
    }

    public StepDiscriminator step() {
        return new StepDiscriminator(this);
    }

    public SequenceBuilder step(Step step) {
        return apply(clone -> clone.steps.add(() -> step));
    }

    public SequenceBuilder step(StepBuilder stepBuilder) {
        return apply(clone -> clone.steps.add(stepBuilder));
    }

    public Sequence build() {
        if (sequence != null) {
            return sequence;
        }
        return sequence = new SequenceImpl(name, id, steps.stream().map(StepBuilder::build).toArray(Step[]::new));
    }

    void id(int id) {
        this.id = id;
    }
}
