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

import io.netty.buffer.Unpooled;
import io.sailrocket.api.HttpMethod;
import io.sailrocket.core.impl.StepImpl;
import io.sailrocket.spi.Validators;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * @author <a href="mailto:stalep@gmail.com">Ståle Pedersen</a>
 */
public class StepBuilder {

    private String endpoint;
    private String payload;
    private HttpMethod httpMethod = HttpMethod.GET;
    private Validators validators;
    private Map<String,String> params = new HashMap<>();

    private StepBuilder() {
    }

    public static StepBuilder stepBuilder() {
        return new StepBuilder();
    }

    private StepBuilder apply(Consumer<StepBuilder> consumer) {
        consumer.accept(this);
        return this;
    }


    public StepBuilder path(String path) {
        return apply(clone -> clone.endpoint = path);
    }

    public StepBuilder payload(String payload) {
        return apply(clone -> clone.payload = payload);
    }

    public StepBuilder httpMethod(HttpMethod httpMethod) {
        return apply(clone -> clone.httpMethod = httpMethod);
    }

    public StepBuilder validators(Validators validators) {
        return apply(clone -> clone.validators = validators);
    }

    public StepBuilder params(String key, String value) {
        return apply(clone -> clone.params.put(key, value));
    }

    public StepImpl build() {
        if(endpoint == null || endpoint.length() == 0)
            throw new IllegalArgumentException("Endpoint need to be set to create a step");

        StepImpl step = new StepImpl();
        step.path(endpoint);
        if(payload != null)
            step.payload(Unpooled.copiedBuffer(payload.getBytes()));
        if(validators != null)
            step.validators(validators);
        step.httpMethod(httpMethod);
        if(params.size() > 0)
            step.params(params);

        return step;
    }

}
