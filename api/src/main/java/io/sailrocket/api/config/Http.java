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

package io.sailrocket.api.config;

import io.sailrocket.api.http.HttpVersion;

/**
 * @author <a href="mailto:stalep@gmail.com">Ståle Pedersen</a>
 */
public class Http {

    private final Url baseUrl;
    private final HttpVersion[] versions;
    private final int maxHttp2Streams;
    private final int pipeliningLimit;
    private final int sharedConnections;
    private final boolean directHttp2;

    public Http(String baseUrl, HttpVersion[] versions, int maxHttp2Streams, int pipeliningLimit, int sharedConnections, boolean directHttp2) {
        this.baseUrl = new Url(baseUrl);
        this.versions = versions;
        this.maxHttp2Streams = maxHttp2Streams;
        this.pipeliningLimit = pipeliningLimit;
        this.sharedConnections = sharedConnections;
        this.directHttp2 = directHttp2;
    }

    public Url baseUrl() {
        return baseUrl;
    }

    public HttpVersion[] versions() {
        return versions;
    }

    public int maxHttp2Streams() {
        return maxHttp2Streams;
    }

    public int pipeliningLimit() {
        return pipeliningLimit;
    }

    public int sharedConnections() {
        return sharedConnections;
    }

    public boolean directHttp2() {
        return directHttp2;
    }
}
