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

import io.sailrocket.api.http.HttpVersion;
import io.sailrocket.core.builders.connection.HttpBase;
import io.sailrocket.core.http.CookieAppender;
import io.sailrocket.core.http.CookieRecorder;
import io.sailrocket.core.steps.HttpRequestStep;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * @author <a href="mailto:stalep@gmail.com">Ståle Pedersen</a>
 */
public class HttpBuilder {

    private final SimulationBuilder parent;
    private String baseUrl;
    private boolean repeatCookies = true;
    private boolean allowHttp1x = true;
    private boolean allowHttp2 = true;

    HttpBuilder(SimulationBuilder parent) {
        this.parent = parent;
    }

    private HttpBuilder apply(Consumer<HttpBuilder> consumer) {
        consumer.accept(this);
        return this;
    }

    public HttpBuilder baseUrl(String url) {
        return apply(clone -> clone.baseUrl = url);
    }

    public HttpBuilder allowHttp1x(boolean allowHttp1x) {
        this.allowHttp1x = allowHttp1x;
        return this;
    }

    public HttpBuilder allowHttp2(boolean allowHttp2) {
        this.allowHttp2 = allowHttp2;
        return this;
    }

    public HttpBuilder repeatCookies(boolean repeatCookies) {
        this.repeatCookies = repeatCookies;
        return this;
    }

    public HttpBase build() {
        if (repeatCookies) {
            for (PhaseBuilder<?> pb : parent.phases()) {
                for (PhaseForkBuilder fork : pb.forks) {
                    for (SequenceBuilder seq : fork.scenario.sequences()) {
                        for (StepBuilder step : seq.steps) {
                            step.forEach(HttpRequestStep.Builder.class, request -> {
                                request.headerAppender(new CookieAppender());
                                request.handler().headerExtractor(new CookieRecorder());
                            });
                        }
                    }
                }
            }
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
        return new HttpBase(baseUrl, httpVersions.toArray(new HttpVersion[0]));
    }

    public SimulationBuilder endHttp() {
        return parent;
    }

}
