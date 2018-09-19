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
package io.sailrocket.api.config;

import java.io.Serializable;

import io.sailrocket.api.session.Session;

/**
 * Sequences are a series of one or more {@link Step}'s that perform one logical unit of operation. Steps within a Sequence are executed in order.
 * State is shared between sequences via the {@link Session}. This allows sequences to pass request scoped state between {@link Step} invocations.
 *
 * Sequences form the basis of a timed operation.
 *
 * @author John O'Hara
 *
 */
public interface Sequence extends Serializable {

    int id();

    void instantiate(Session session, int id);

    void reserve(Session session);

    String name();

    String phase();
}
