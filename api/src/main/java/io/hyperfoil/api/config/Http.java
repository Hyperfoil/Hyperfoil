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

package io.hyperfoil.api.config;

import java.io.Serializable;

import io.hyperfoil.api.http.HttpVersion;

/**
 * @author <a href="mailto:stalep@gmail.com">Ståle Pedersen</a>
 */
public class Http implements Serializable {

   private final boolean isDefault;
   private final Protocol protocol;
   private final String host;
   private final int port;
   private final String[] addresses;
   private final HttpVersion[] versions;
   private final int maxHttp2Streams;
   private final int pipeliningLimit;
   private final int sharedConnections;
   private final boolean directHttp2;
   private final long requestTimeout;
   private final boolean rawBytesHandlers;
   private final KeyManager keyManager;
   private final TrustManager trustManager;

   public Http(boolean isDefault, Protocol protocol, String host, int port, String[] addresses,
               HttpVersion[] versions, int maxHttp2Streams, int pipeliningLimit, int sharedConnections,
               boolean directHttp2, long requestTimeout, boolean rawBytesHandlers,
               KeyManager keyManager, TrustManager trustManager) {
      this.isDefault = isDefault;
      this.protocol = protocol;
      this.host = host;
      this.port = port;
      this.addresses = addresses;
      this.versions = versions;
      this.maxHttp2Streams = maxHttp2Streams;
      this.pipeliningLimit = pipeliningLimit;
      this.sharedConnections = sharedConnections;
      this.directHttp2 = directHttp2;
      this.requestTimeout = requestTimeout;
      this.rawBytesHandlers = rawBytesHandlers;
      this.keyManager = keyManager;
      this.trustManager = trustManager;
   }

   public Protocol protocol() {
      return protocol;
   }

   public String host() {
      return host;
   }

   public int port() {
      return port;
   }

   public HttpVersion[] versions() {
      return versions;
   }

   public int maxHttp2Streams() {
      return maxHttp2Streams;
   }

   public int pipeliningLimit() {
      return pipeliningLimit;
   }

   public int sharedConnections() {
      return sharedConnections;
   }

   public boolean directHttp2() {
      return directHttp2;
   }

   public boolean isDefault() {
      return isDefault;
   }

   public long requestTimeout() {
      return requestTimeout;
   }

   public String[] addresses() {
      return addresses;
   }

   public boolean rawBytesHandlers() {
      return rawBytesHandlers;
   }

   public TrustManager trustManager() {
      return trustManager;
   }

   public KeyManager keyManager() {
      return keyManager;
   }

   public static class KeyManager implements Serializable {
      private final String storeType;
      private final byte[] storeBytes;
      private final String password;
      private final String alias;
      private final byte[] certBytes;
      private final byte[] keyBytes;

      public KeyManager(String storeType, byte[] storeBytes, String password, String alias, byte[] certBytes, byte[] keyBytes) {
         this.storeType = storeType;
         this.storeBytes = storeBytes;
         this.password = password;
         this.alias = alias;
         this.certBytes = certBytes;
         this.keyBytes = keyBytes;
      }

      public String storeType() {
         return storeType;
      }

      public byte[] storeBytes() {
         return storeBytes;
      }

      public String password() {
         return password;
      }

      public String alias() {
         return alias;
      }

      public byte[] certBytes() {
         return certBytes;
      }

      public byte[] keyBytes() {
         return keyBytes;
      }
   }

   public static class TrustManager implements Serializable {
      private final String storeType;
      private final byte[] storeBytes;
      private final String password;
      private final byte[] certBytes;

      public TrustManager(String storeType, byte[] storeBytes, String password, byte[] certBytes) {
         this.storeType = storeType;
         this.storeBytes = storeBytes;
         this.password = password;
         this.certBytes = certBytes;
      }

      public String storeType() {
         return storeType;
      }

      public byte[] storeBytes() {
         return storeBytes;
      }

      public String password() {
         return password;
      }

      public byte[] certBytes() {
         return certBytes;
      }
   }
}
