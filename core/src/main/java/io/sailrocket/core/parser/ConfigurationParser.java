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
package io.sailrocket.core.parser;

import io.sailrocket.api.config.Benchmark;
import io.sailrocket.core.builders.BenchmarkBuilder;
import io.sailrocket.core.parser.builders.AbstractConfigurationBuilder;
import io.sailrocket.core.parser.builders.HostsConfigurationBuilder;
import io.sailrocket.core.parser.builders.PhasesConfigurationBuilder;
import io.sailrocket.core.parser.builders.PropertyConfigurationBuilder;
import io.sailrocket.core.parser.builders.SimulationConfigurationBuilder;
import io.sailrocket.core.parser.builders.SlaConfigurationBuilder;
import io.sailrocket.core.parser.builders.StateConfigurationBuilder;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;

public class ConfigurationParser extends AbstractConfigurationBuilder<Map, BenchmarkBuilder> {

    public ConfigurationParser() {
        subBuilders.put("name", PropertyConfigurationBuilder.instance("name"));
        subBuilders.put(HostsConfigurationBuilder.key, HostsConfigurationBuilder.instance());
        subBuilders.put(SimulationConfigurationBuilder.key, SimulationConfigurationBuilder.instance());
        subBuilders.put(PhasesConfigurationBuilder.key, PhasesConfigurationBuilder.instance());
        subBuilders.put(SlaConfigurationBuilder.key, SlaConfigurationBuilder.instance());
        subBuilders.put(StateConfigurationBuilder.key, StateConfigurationBuilder.instance());
    }

    public Benchmark buildBenchmark(InputStream configurationStream) throws ConfigurationNotDefinedException, ConfigurationParserException {

        if (configurationStream == null)
            throw new ConfigurationNotDefinedException();

        Yaml yaml = new Yaml();
        Object data = yaml.load(configurationStream);

        //instantiate new benchmark builder
        BenchmarkBuilder benchmarkBuilder = BenchmarkBuilder.builder();

        build((Map) data, benchmarkBuilder);

        return benchmarkBuilder.build();
    }

    @Override
    public void build(Map configuration, BenchmarkBuilder target) throws ConfigurationParserException {

        //populate benchmark model
        callSubBuilders(configuration, target);

    }

}
