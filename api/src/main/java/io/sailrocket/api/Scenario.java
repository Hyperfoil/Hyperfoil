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
 * A scenario is a workflow that consists of a set of {@link Sequence sequences}
 * undertaken by an end user to emulate use case logic that an end user would perform against a service.
 * A scenario can branch depending on the outcome of the sequences contained in the Scenario.
 * State may be shared between sequences in a scenario through the {@link Session}.
 */
public interface Scenario {
   /**
    * Since this method is part of the 0-alloc loop it should not allocate the iterator.
    */
    Sequence[] initialSequences();

    Sequence[] sequences();

    String[] objectVars();

    String[] intVars();

   /**
    * @return Maximum number of concurrent requests.
    */
    int maxRequests();

   /**
    * @return Maximum number of concurrently existing sequences.
    */
    int maxSequences();
}
