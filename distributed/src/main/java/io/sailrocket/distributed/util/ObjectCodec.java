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

import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;
import io.vertx.core.eventbus.impl.codecs.ByteArrayMessageCodec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;

public class ObjectCodec<T> implements MessageCodec<T, T> {

    @Override
    public void encodeToWire(Buffer buffer, T object) {


        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ByteArrayMessageCodec byteArrayMessageCodec = new ByteArrayMessageCodec();

        try {
            ObjectOutput out = new ObjectOutputStream(bos);
            out.writeObject(object);
            out.flush();

            byteArrayMessageCodec.encodeToWire(buffer, bos.toByteArray());

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                bos.close();
            } catch (IOException ex) {
                // ignore close exception
            }
        }
    }

    @Override
    public T decodeFromWire(int position, Buffer buffer) {

        ByteArrayMessageCodec byteArrayMessageCodec = new ByteArrayMessageCodec();

        ObjectInput in = null;
        ByteArrayInputStream bis = new ByteArrayInputStream(byteArrayMessageCodec.decodeFromWire(position, buffer));
        T object = null;

        try {
            in = new ObjectInputStream(bis);
            object = (T) in.readObject();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (IOException ex) {
                // ignore close exception
            }
        }
        return object;

    }

    @Override
    public T transform(T object) {
        // If a message is sent *locally* across the event bus.
        // This example sends message just as is
        return object;
    }

    @Override
    public String name() {
        // Each codec must have a unique name.
        // This is used to identify a codec when sending a message and for unregistering codecs.
        return this.getClass().getName();
    }

    @Override
    public byte systemCodecID() {
        // Always -1
        return -1;
    }


}
