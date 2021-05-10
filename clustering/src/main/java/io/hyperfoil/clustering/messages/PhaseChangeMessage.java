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

package io.hyperfoil.clustering.messages;

import io.hyperfoil.api.session.PhaseInstance;
import io.hyperfoil.core.util.Util;

public class PhaseChangeMessage extends AgentStatusMessage {
   private final String phase;
   private final PhaseInstance.Status status;
   private final boolean sessionLimitExceeded;
   private final String cpuUsage;
   private final Throwable error;

   public PhaseChangeMessage(String senderId, String runId, String phase, PhaseInstance.Status status, boolean sessionLimitExceeded, String cpuUsage, Throwable error) {
      super(senderId, runId);
      this.phase = phase;
      this.status = status;
      this.sessionLimitExceeded = sessionLimitExceeded;
      this.cpuUsage = cpuUsage;
      this.error = error;
   }

   @Override
   public String toString() {
      final StringBuilder sb = new StringBuilder("PhaseChangeMessage{");
      sb.append("senderId='").append(senderId).append('\'');
      sb.append(", phase=").append(phase);
      sb.append(", status=").append(status);
      sb.append(", cpuUsage=").append(cpuUsage);
      sb.append(", error=").append(Util.explainCauses(error));
      sb.append('}');
      return sb.toString();
   }

   public String phase() {
      return phase;
   }

   public PhaseInstance.Status status() {
      return status;
   }

   public boolean sessionLimitExceeded() {
      return sessionLimitExceeded;
   }

   public Throwable getError() {
      return error;
   }

   public String cpuUsage() {
      return cpuUsage;
   }

   public static class Codec extends ObjectCodec<PhaseChangeMessage> {}
}

