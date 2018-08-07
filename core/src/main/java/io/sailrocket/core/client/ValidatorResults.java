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

package io.sailrocket.core.client;

import java.util.concurrent.atomic.LongAdder;

/**
 * @author <a href="mailto:stalep@gmail.com">Ståle Pedersen</a>
 */
public class ValidatorResults {

    private LongAdder statusValid = new LongAdder();
    private LongAdder statusInvalid = new LongAdder();

    private LongAdder headerValid = new LongAdder();
    private LongAdder headerInvalid = new LongAdder();

    private LongAdder bodyValid = new LongAdder();
    private LongAdder bodyInvalid = new LongAdder();

    public ValidatorResults(){
    }

    public void addStatus(boolean valid) {
        if(valid)
            statusValid.increment();
        else
            statusInvalid.increment();
    }

    public void addHeader(boolean valid) {
        if(valid)
            headerValid.increment();
        else
            headerInvalid.increment();
    }

    public void addBody(boolean valid) {
        if(valid)
            bodyValid.increment();
        else
            bodyInvalid.increment();
    }

    public LongAdder statusValid() {
        return statusValid;
    }

    public LongAdder statusInvalid() {
        return statusInvalid;
    }

    public LongAdder headerValid() {
        return headerValid;
    }

    public LongAdder headerInvalid() {
        return headerInvalid;
    }

    public LongAdder bodyValid() {
        return bodyValid;
    }

    public LongAdder bodyInvalid() {
        return bodyInvalid;
    }
}
