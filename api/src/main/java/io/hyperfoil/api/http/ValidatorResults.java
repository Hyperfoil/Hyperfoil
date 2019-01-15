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

package io.hyperfoil.api.http;

/**
 * @author <a href="mailto:stalep@gmail.com">Ståle Pedersen</a>
 */
@Deprecated
public class ValidatorResults {

    private long statusValid;
    private long statusInvalid;

    private long headerValid;
    private long headerInvalid;

    private long bodyValid;
    private long bodyInvalid;

    public ValidatorResults(){
    }

    public void addStatus(boolean valid) {
        if(valid)
            statusValid++;
        else
            statusInvalid++;
    }

    public void addHeader(boolean valid) {
        if(valid)
            headerValid++;
        else
            headerInvalid++;
    }

    public void addBody(boolean valid) {
        if(valid)
            bodyValid++;
        else
            bodyInvalid++;
    }

    public long statusValid() {
        return statusValid;
    }

    public long statusInvalid() {
        return statusInvalid;
    }

    public long headerValid() {
        return headerValid;
    }

    public long headerInvalid() {
        return headerInvalid;
    }

    public long bodyValid() {
        return bodyValid;
    }

    public long bodyInvalid() {
        return bodyInvalid;
    }
}
