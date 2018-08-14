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

import io.sailrocket.spi.HttpBase;
import io.sailrocket.spi.HttpHeader;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * @author <a href="mailto:stalep@gmail.com">Ståle Pedersen</a>
 */
public class HttpBuilder {

    private Map<String,String> header;
    private String baseUrl;
    private int httpStatus;

    private HttpBuilder() {
        header = new HashMap<>();
    }

    public static HttpBuilder httpBuilder() {
        return new HttpBuilder();
    }

    private HttpBuilder apply(Consumer<HttpBuilder> consumer) {
        consumer.accept(this);
        return this;
    }

    public HttpBuilder baseUrl(String url) {
        return apply(clone -> clone.baseUrl = url);
    }

    public HttpBuilder status(int status) {
        return apply(clone -> clone.httpStatus = status);
    }

    public HttpBuilder acceptHeader(String key, String value) {
        return apply(clone -> clone.header.put(key, value));
    }

    public HttpBase build() {
        return new HttpBase(new HttpHeader(header), baseUrl, httpStatus);
    }

}
