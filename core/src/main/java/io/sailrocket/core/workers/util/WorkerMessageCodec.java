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

package io.sailrocket.core.workers.util;

import io.sailrocket.core.workers.WorkerStatus;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;
import io.vertx.core.json.JsonObject;

public class WorkerMessageCodec implements MessageCodec<WorkerMessage, WorkerMessage> {

  @Override
  public void encodeToWire(Buffer buffer, WorkerMessage workerMessage) {
    // Easiest ways is using JSON object
      //todo: make this more optimal
    JsonObject jsonToEncode = new JsonObject();
    jsonToEncode.put("status", workerMessage.statusCode());
    jsonToEncode.put("senderId", workerMessage.senderId());

    // Encode object to string
    String jsonToStr = jsonToEncode.encode();

    // Length of JSON: is NOT characters count
    int length = jsonToStr.getBytes().length;

    // Write data into given buffer
    buffer.appendInt(length);
    buffer.appendString(jsonToStr);
  }

  @Override
  public WorkerMessage decodeFromWire(int position, Buffer buffer) {
    // My custom message starting from this *position* of buffer
    int _pos = position;

    // Length of JSON
    int length = buffer.getInt(_pos);

    // Get JSON string by it`s length
    // Jump 4 because getInt() == 4 bytes
    String jsonStr = buffer.getString(_pos+=4, _pos+=length);
    JsonObject contentJson = new JsonObject(jsonStr);

    // Get fields
    int statusCode = contentJson.getInteger("statusCode");
    String senderId = contentJson.getString("resultCode");

    // We can finally create custom message object
    return new WorkerMessage(WorkerStatus.find(statusCode), senderId);
  }

  @Override
  public WorkerMessage transform(WorkerMessage workerMessage) {
    // If a message is sent *locally* across the event bus.
    // This example sends message just as is
    return workerMessage;
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
