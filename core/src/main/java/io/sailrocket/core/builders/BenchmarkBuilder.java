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

import io.sailrocket.api.Simulation;

import java.util.function.Consumer;

/**
 * @author <a href="mailto:stalep@gmail.com">Ståle Pedersen</a>
 */
public class BenchmarkBuilder {

    private String name;
    private Simulation simulation;

    private BenchmarkBuilder() {}

    public static BenchmarkBuilder builder() { return new BenchmarkBuilder(); }

    private BenchmarkBuilder apply(Consumer<BenchmarkBuilder> consumer) {
        consumer.accept(this);
        return this;
    }

    private BenchmarkBuilder name(String name) {
        return apply(clone -> clone.name = name);
    }

    private BenchmarkBuilder simulation(Simulation simulation) {
        return apply(clone -> clone.simulation = simulation);
    }

}
