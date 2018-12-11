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
package io.sailrocket.api.http;

import java.io.Serializable;

import io.netty.buffer.ByteBuf;
import io.sailrocket.api.config.ServiceLoadedBuilder;
import io.sailrocket.api.connection.Request;

/**
 * @author <a href="mailto:stalep@gmail.com">Ståle Pedersen</a>
 */
public interface BodyValidator extends Serializable {
   void beforeData(Request request);
   void validateData(Request request, ByteBuf chunk);
   boolean validate(Request request);

   interface BuilderFactory extends ServiceLoadedBuilder.Factory<BodyValidator> {}
}
