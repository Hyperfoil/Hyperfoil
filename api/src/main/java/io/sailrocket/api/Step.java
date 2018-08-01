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

import java.util.concurrent.CompletableFuture;

/**
 * A step represents a single http request/response. Steps are chained together in a {@link Sequence}.
 * {@link Sequence} contains one or more steps.
 *
 * @author John O'Hara
 *
 */
public interface Step {

    /**
     * URL path of request
     * @param path the URL pth
     * @return this
     */
    Step path(String path);

    Step param(String name, String value);

    Step validator(Validator<?> validator);

    Step next(Step next);

}
