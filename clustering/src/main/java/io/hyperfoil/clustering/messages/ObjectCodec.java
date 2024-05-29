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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;

import org.infinispan.commons.io.LazyByteArrayOutputStream;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;
import io.vertx.core.eventbus.impl.codecs.ByteArrayMessageCodec;

public class ObjectCodec<T> implements MessageCodec<T, T> {

   @Override
   public void encodeToWire(Buffer buffer, T object) {


      LazyByteArrayOutputStream bos = new LazyByteArrayOutputStream();

      try {
         ObjectOutput out = new ObjectOutputStream(bos);
         out.writeObject(object);
         out.flush();

         buffer.appendInt(bos.size());
         buffer.appendBytes(bos.getRawBuffer(), 0, bos.size());

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

      try {
         in = new ObjectInputStream(bis);
         @SuppressWarnings("unchecked")
         T object = (T) in.readObject();
         return object;
      } catch (IOException | ClassNotFoundException e) {
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
      return null;
   }

   @Override
   public T transform(T object) {
      // We cannot protect the sender against mutation in codec because the encodeToWire is not called
      // synchronously even if the eventBus.send() is invoked from event loop.
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

   public static class ArrayList extends ObjectCodec<java.util.ArrayList> {
      @SuppressWarnings("unchecked")
      @Override
      public java.util.ArrayList transform(java.util.ArrayList object) {
         return new java.util.ArrayList(object);
      }
   }
}
