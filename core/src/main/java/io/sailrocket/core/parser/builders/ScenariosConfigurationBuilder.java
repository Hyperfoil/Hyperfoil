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
package io.sailrocket.core.parser.builders;

import io.sailrocket.core.builders.SimulationBuilder;
import io.sailrocket.core.parser.ConfigurationParserException;

import java.util.Map;

public class ScenariosConfigurationBuilder extends AbstractConfigurationBuilder<Map, SimulationBuilder> {

    public static String key = "simulations";

    ScenariosConfigurationBuilder() {
        this.subBuilders.put(SequencesConfigurationBuilder.key, SequencesConfigurationBuilder.instance());
    }

    public static ScenariosConfigurationBuilder instance() {
        return new ScenariosConfigurationBuilder();
    }

    @Override
    public void build(Map configuration, SimulationBuilder target) throws ConfigurationParserException {

//       ScenarioBuilder scenarioBuilder = new ScenarioBuilder(null);
//
//        callSubBuilders(configuration, scenarioBuilder);
//
//        target.addPhase("test").atOnce(1).scenario(scenarioBuilder);

    }
}
