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

package io.hyperfoil.api.config;

import io.hyperfoil.api.http.HttpVersion;
import io.hyperfoil.util.Util;

import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href="mailto:stalep@gmail.com">Ståle Pedersen</a>
 */
public class HttpBuilder {

    private final BenchmarkBuilder parent;
    private Http http;
    private Protocol protocol;
    private String host;
    private int port = -1;
    private boolean allowHttp1x = true;
    private boolean allowHttp2 = true;
    private int sharedConnections = 1;
    private int maxHttp2Streams = 100;
    private int pipeliningLimit = 1;
    private boolean directHttp2 = false;
    private long requestTimeout = 30000;

    public static HttpBuilder forTesting() {
        return new HttpBuilder(null);
    }

    HttpBuilder(BenchmarkBuilder parent) {
        this.parent = parent;
    }

    String authority() {
        if (host == null) {
            return null;
        } else if (protocol != null) {
            return host + ":" + protocol.portOrDefault(port);
        } else {
            return host + ":" + Protocol.fromPort(port).portOrDefault(port);
        }
    }

    public HttpBuilder protocol(Protocol protocol) {
        this.protocol = protocol;
        return this;
    }

    public HttpBuilder host(String host) {
        int lastColon = host.lastIndexOf(':');
        if (lastColon < 0) {
            this.host = host;
            return this;
        }
        int firstColon = host.indexOf(':');
        if (firstColon == lastColon) {
            String maybePort = host.substring(lastColon + 1);
            try {
                this.port = Integer.parseInt(maybePort);
                this.host = host.substring(0, lastColon);
            } catch (NumberFormatException e) {
                this.protocol = Protocol.fromScheme(host.substring(0, firstColon));
                this.host = host.substring(firstColon + 3);
            }
        } else {
            this.protocol = Protocol.fromScheme(host.substring(0, firstColon));
            this.host = host.substring(firstColon + 3, lastColon);
            String portString = host.substring(lastColon + 1);
            this.port = Integer.parseInt(portString);
        }
        return this;
    }

    public HttpBuilder port(int port) {
        this.port = port;
        return this;
    }


    public HttpBuilder allowHttp1x(boolean allowHttp1x) {
        this.allowHttp1x = allowHttp1x;
        return this;
    }

    public HttpBuilder allowHttp2(boolean allowHttp2) {
        this.allowHttp2 = allowHttp2;
        return this;
    }

    public BenchmarkBuilder endHttp() {
        return parent;
    }

    public HttpBuilder sharedConnections(int sharedConnections) {
        this.sharedConnections = sharedConnections;
        return this;
    }

    public HttpBuilder maxHttp2Streams(int maxStreams) {
        this.maxHttp2Streams = maxStreams;
        return this;
    }

    public HttpBuilder pipeliningLimit(int limit) {
        this.pipeliningLimit = limit;
        return this;
    }

    public HttpBuilder directHttp2(boolean directHttp2) {
        this.directHttp2 = directHttp2;
        return this;
    }

    public HttpBuilder requestTimeout(long requestTimeout) {
        this.requestTimeout = requestTimeout;
        return this;
    }

    public HttpBuilder requestTimeout(String requestTimeout) {
        if ("none".equals(requestTimeout)) {
            this.requestTimeout = -1;
        } else {
            this.requestTimeout = Util.parseToMillis(requestTimeout);
        }
        return this;
    }

    public long requestTimeout() {
        return requestTimeout;
    }

    public void prepareBuild() {
    }

    public Http build(boolean isDefault) {
        if (http != null) {
            if (isDefault != http.isDefault()) {
                throw new IllegalArgumentException("Already built as isDefault=" + http.isDefault());
            }
            return http;
        }
        List<HttpVersion> httpVersions = new ArrayList<>();
        // The order is important here because it will be provided to the ALPN
        if (allowHttp2) {
            httpVersions.add(HttpVersion.HTTP_2_0);
        }
        if (allowHttp1x) {
            httpVersions.add(HttpVersion.HTTP_1_1);
            httpVersions.add(HttpVersion.HTTP_1_0);
        }
        if (directHttp2) {
            throw new UnsupportedOperationException("Direct HTTP/2 not implemented");
        }
        Protocol protocol = this.protocol != null ? this.protocol : Protocol.fromPort(port);
        return http = new Http(isDefault, protocol, host, protocol.portOrDefault(port),
              httpVersions.toArray(new HttpVersion[0]), maxHttp2Streams, pipeliningLimit,
              sharedConnections, directHttp2, requestTimeout);
    }
}
