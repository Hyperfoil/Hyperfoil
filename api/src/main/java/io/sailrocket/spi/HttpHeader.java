/*
 * JBoss, Home of Professional Open Source
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

package io.sailrocket.spi;

import io.vertx.core.MultiMap;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * @author <a href="mailto:stalep@gmail.com">Ståle Pedersen</a>
 */
public class HttpHeader {

    private final Map<String, String> headerValues;

    public HttpHeader(Map<String,String> headerValues) {
        this.headerValues = headerValues;
    }

    public HttpHeader(MultiMap multiMap) {
        Iterator<Map.Entry<String,String>> iterator = multiMap.iterator();
        headerValues = new HashMap<>(multiMap.size());
        while(iterator.hasNext()) {
            Map.Entry<String, String> entry = iterator.next();
            headerValues.put(entry.getKey(), entry.getValue());
        }
    }

    public String getValue(String key) {
        return headerValues.get(key);
    }

    public boolean hasKey(String key) {
        return headerValues.containsKey(key);
    }
}
