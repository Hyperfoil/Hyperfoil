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

package io.sailrocket.core.builders.connection;

import io.sailrocket.api.http.HttpVersion;

/**
 * @author <a href="mailto:stalep@gmail.com">Ståle Pedersen</a>
 */
public class HttpBase {

    private final Url baseUrl;
    private final HttpVersion[] versions;
    // TODO: direct HTTP/2 (no h2c)

    public HttpBase(String baseUrl, HttpVersion[] versions) {
        this.baseUrl = new Url(baseUrl);
        this.versions = versions;
    }

    public Url baseUrl() {
        return baseUrl;
    }

    public HttpVersion[] versions() {
        return versions;
    }
}
