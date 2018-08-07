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

package io.sailrocket.spi;

/**
 * @author <a href="mailto:stalep@gmail.com">Ståle Pedersen</a>
 */
public class Validators {

    private final StatusValidator statusValidator;
    private final HeaderValidator headerValidator;
    private final BodyValidator bodyValidator;

    public Validators(StatusValidator statusValidator, HeaderValidator headerValidator, BodyValidator bodyValidator) {
        this.statusValidator = statusValidator;
        this.headerValidator = headerValidator;
        this.bodyValidator = bodyValidator;
    }

    public StatusValidator statusValidator() {
        return statusValidator;
    }

    public boolean hasStatusValidator() {
        return statusValidator != null;
    }

    public HeaderValidator headerValidator() {
        return headerValidator;
    }

    public boolean hasHeaderValidator() {
        return headerValidator != null;
    }

    public BodyValidator bodyValidator() {
        return bodyValidator;
    }

    public boolean hasBodyValidator() {
        return bodyValidator != null;
    }
}
