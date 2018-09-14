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
package io.sailrocket.core.builder;

import io.sailrocket.api.Benchmark;
import io.sailrocket.core.parser.ConfigurationNotDefinedException;
import io.sailrocket.core.parser.ConfigurationParser;
import io.sailrocket.core.parser.ConfigurationParserException;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.io.InputStream;

import static org.junit.Assert.fail;

public class YamlBuilderTestCase {

    @Test
    @Ignore
    public void ComplexYamlBuilderTest() {

        Benchmark benchmark = buildBenchmark(this.getClass().getClassLoader().getResourceAsStream("scenarios/complex.yaml"));

        Assert.assertEquals("complex benchmark", benchmark.name());
    }

    @Test
    @Ignore
    public void SimpleYamlBuilderTest() {

        Benchmark benchmark = buildBenchmark(this.getClass().getClassLoader().getResourceAsStream("scenarios/simple.yaml"));

        Assert.assertEquals("simple benchmark", benchmark.name());


    }

    private Benchmark buildBenchmark(InputStream inputStream){
        if (inputStream == null)
            fail("Could not find benchmark configuration");


        try {
            Benchmark benchmark = new ConfigurationParser().buildBenchmark(inputStream);

            Assert.assertNotNull(benchmark);

            return benchmark;

        } catch (ConfigurationNotDefinedException e) {
            e.printStackTrace();
            fail("Configuration not defined");
        } catch (ConfigurationParserException e) {
            e.printStackTrace();
            fail("Error occurred during parsing");
        }
        return null;
    }
}
