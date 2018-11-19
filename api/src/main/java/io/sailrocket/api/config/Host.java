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
 */
package io.sailrocket.api.config;

import java.io.Serializable;

public class Host implements Serializable {
    public final String name;
    public final String hostname;
    public final String username;
    public final int port;

    public Host(String name, String hostname, String username, int port) {
        this.name = name;
        this.hostname = hostname;
        this.username = username;
        this.port = port;
    }

    public static Host parse(String name, String usernameHostPort) {
        int atIndex = usernameHostPort.indexOf('@');
        int colonIndex = usernameHostPort.lastIndexOf(':');
        String hostname = usernameHostPort.substring(atIndex + 1, colonIndex >= 0 ? colonIndex - 1 : usernameHostPort.length());
        String username = atIndex >= 0 ? usernameHostPort.substring(0, atIndex) : null;
        int port = colonIndex >= 0 ? Integer.parseInt(usernameHostPort.substring(colonIndex + 1)) : -1;
        return new Host(name, hostname, username, port);
    }
}
