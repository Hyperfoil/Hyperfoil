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

import io.sailrocket.core.api.PhaseInstance;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;
import io.vertx.core.json.JsonObject;

public class PhaseChangeMessage {
  private final String senderId;
  private final String phase;
  private final PhaseInstance.Status status;

  public PhaseChangeMessage(String senderId, String phase, PhaseInstance.Status status) {
    this.senderId = senderId;
    this.phase = phase;
    this.status = status;
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

  public String phase() {
    return phase;
  }

  public PhaseInstance.Status status() {
    return status;
  }

  public static class Codec implements MessageCodec<PhaseChangeMessage, PhaseChangeMessage> {

    @Override
    public void encodeToWire(Buffer buffer, PhaseChangeMessage phaseChangeMessage) {
      // Easiest ways is using JSON object
        //todo: make this more optimal
      JsonObject jsonToEncode = new JsonObject();
      jsonToEncode.put("senderId", phaseChangeMessage.senderId());
      jsonToEncode.put("phase", phaseChangeMessage.phase());
      jsonToEncode.put("status", phaseChangeMessage.status());

      // Encode object to string
      String jsonToStr = jsonToEncode.encode();

      // Length of JSON: is NOT characters count
      int length = jsonToStr.getBytes().length;

      // Write data into given buffer
      buffer.appendInt(length);
      buffer.appendString(jsonToStr);
    }

    @Override
    public PhaseChangeMessage decodeFromWire(int position, Buffer buffer) {
      // My custom message starting from this *position* of buffer
      int _pos = position;

      // Length of JSON
      int length = buffer.getInt(_pos);

      // Get JSON string by it`s length
      // Jump 4 because getInt() == 4 bytes
      String jsonStr = buffer.getString(_pos+=4, _pos+=length);
      JsonObject contentJson = new JsonObject(jsonStr);

      // Get fields
      String senderId = contentJson.getString("senderId");
      String phase = contentJson.getString("phase");
      PhaseInstance.Status status = PhaseInstance.Status.valueOf(contentJson.getString("status"));

      // We can finally create custom message object
      return new PhaseChangeMessage(senderId, phase, status);
    }

    @Override
    public PhaseChangeMessage transform(PhaseChangeMessage phaseChangeMessage) {
      // If a message is sent *locally* across the event bus.
      // This example sends message just as is
      return phaseChangeMessage;
    }

     @Override
    public String name() {
      // Each codec must have a unique name.
      // This is used to identify a codec when sending a message and for unregistering codecs.
      return this.getClass().getSimpleName();
    }

    @Override
    public byte systemCodecID() {
      // Always -1
      return -1;
    }


  }
}

