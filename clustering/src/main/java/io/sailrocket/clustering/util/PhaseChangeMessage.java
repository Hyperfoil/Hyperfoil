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

package io.sailrocket.clustering.util;

import java.io.Serializable;

import io.sailrocket.api.session.PhaseInstance;
import io.sailrocket.util.Immutable;

public class PhaseChangeMessage implements Serializable, Immutable {
  private final String senderId;
  private final String runId;
  private final String phase;
  private final PhaseInstance.Status status;
  private final boolean successful;

  public PhaseChangeMessage(String senderId, String runId, String phase, PhaseInstance.Status status, boolean successful) {
    this.senderId = senderId;
    this.runId = runId;
    this.phase = phase;
    this.status = status;
    this.successful = successful;
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("SimulationMessage{");

    sb.append("senderId='").append(senderId).append('\'');
    sb.append(", phase=").append(phase);
    sb.append(", status=").append(status);
    sb.append('}');
    return sb.toString();
  }

  public String senderId() {
    return senderId;
  }

  public String runId() {
    return runId;
  }

  public String phase() {
    return phase;
  }

  public PhaseInstance.Status status() {
    return status;
  }

  public boolean isSuccessful() {
    return successful;
  }

  public static class Codec extends ObjectCodec<PhaseChangeMessage> {}
}

