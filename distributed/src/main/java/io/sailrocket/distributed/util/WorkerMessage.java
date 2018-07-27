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

package io.sailrocket.distributed.util;

import io.sailrocket.distributed.WorkerStatus;

public class WorkerMessage {

  private final WorkerStatus statusCode;
  private final String senderId;

  public WorkerMessage(WorkerStatus statusCode,
                       String senderId) {
    this.statusCode = statusCode;
    this.senderId = senderId;
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("WorkerMessage{");
    sb.append("status=").append(statusCode);
    sb.append(", senderId='").append(senderId).append('\'');
    sb.append('}');
    return sb.toString();
  }

  public WorkerStatus statusCode() {
    return statusCode;
  }

  public String senderId() {
    return senderId;
  }
}

