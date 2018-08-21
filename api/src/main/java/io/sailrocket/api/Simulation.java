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
package io.sailrocket.api;

/**
 * Simulation represents a collection of workflows (scenarios) against a target application.  
 *
 * A Simulation can have an even mix or used defined mix of scenarios
 * State is not shared between scenarios in a simulation,
 * and scenarios can be run independently and concurrently with other scenarios within a simulation.
 */
public interface Simulation {

    Simulation scenario(Scenario scenario);

    Simulation mixStrategy(MixStrategy mixStrategy);

}
