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

package io.hyperfoil.http.config;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.Rewritable;
import io.hyperfoil.core.util.Util;
import io.hyperfoil.http.api.HttpVersion;

/**
 * @author <a href="mailto:stalep@gmail.com">Ståle Pedersen</a>
 */
public class HttpBuilder implements Rewritable<HttpBuilder> {

   private final HttpPluginBuilder parent;
   private Http http;
   private Protocol protocol;
   private String host;
   private int port = -1;
   private List<String> addresses = new ArrayList<>();
   private boolean allowHttp1x = true;
   private boolean allowHttp2 = true;
   private int sharedConnections = 1;
   private int maxHttp2Streams = 100;
   private int pipeliningLimit = 1;
   private boolean directHttp2 = false;
   private long requestTimeout = 30000;
   private boolean rawBytesHandlers = true;
   private KeyManagerBuilder keyManager = new KeyManagerBuilder();
   private TrustManagerBuilder trustManager = new TrustManagerBuilder();
   private boolean privatePools = false;

   public static HttpBuilder forTesting() {
      return new HttpBuilder(null);
   }

   HttpBuilder(HttpPluginBuilder parent) {
      this.parent = parent;
   }

   String authority() {
      if (host == null) {
         return null;
      } else if (protocol != null) {
         return host + ":" + protocol.portOrDefault(port);
      } else {
         return host + ":" + Protocol.fromPort(port).portOrDefault(port);
      }
   }

   public HttpBuilder protocol(Protocol protocol) {
      if (this.protocol != null) {
         throw new BenchmarkDefinitionException("Duplicate 'protocol'");
      }
      this.protocol = protocol;
      return this;
   }

   public String host() {
      return host;
   }

   public HttpBuilder host(String host) {
      if (this.host != null) {
         throw new BenchmarkDefinitionException("Duplicate 'host'. Are you missing '-'s?");
      }
      URL result;
      String spec = host;
      if (!spec.contains("://")) {
         spec = "http://" + spec;
      }
      try {
         result = new URL(spec);
      } catch (MalformedURLException e) {
         throw new BenchmarkDefinitionException("Failed to parse host:port", e);
      }
      URL url = result;
      this.protocol = protocol == null ? Protocol.fromScheme(url.getProtocol()) : protocol;
      this.host = url.getHost();
      this.port = url.getPort();
      if (url.getFile() != null && !url.getFile().isEmpty()) {
         throw new BenchmarkDefinitionException("Host must not contain any path: " + host);
      }
      return this;
   }

   public HttpBuilder port(int port) {
      if (this.port > 0) {
         throw new BenchmarkDefinitionException("Duplicate 'port'");
      }
      this.port = port;
      return this;
   }


   public HttpBuilder allowHttp1x(boolean allowHttp1x) {
      this.allowHttp1x = allowHttp1x;
      return this;
   }

   public HttpBuilder allowHttp2(boolean allowHttp2) {
      this.allowHttp2 = allowHttp2;
      return this;
   }

   public HttpPluginBuilder endHttp() {
      return parent;
   }

   public HttpBuilder sharedConnections(int sharedConnections) {
      this.sharedConnections = sharedConnections;
      return this;
   }

   public HttpBuilder maxHttp2Streams(int maxStreams) {
      this.maxHttp2Streams = maxStreams;
      return this;
   }

   public HttpBuilder pipeliningLimit(int limit) {
      this.pipeliningLimit = limit;
      return this;
   }

   public HttpBuilder directHttp2(boolean directHttp2) {
      this.directHttp2 = directHttp2;
      return this;
   }

   public HttpBuilder requestTimeout(long requestTimeout) {
      this.requestTimeout = requestTimeout;
      return this;
   }

   public HttpBuilder requestTimeout(String requestTimeout) {
      if ("none".equals(requestTimeout)) {
         this.requestTimeout = -1;
      } else {
         this.requestTimeout = Util.parseToMillis(requestTimeout);
      }
      return this;
   }

   public long requestTimeout() {
      return requestTimeout;
   }

   public HttpBuilder addAddress(String address) {
      addresses.add(address);
      return this;
   }

   public HttpBuilder rawBytesHandlers(boolean rawBytesHandlers) {
      this.rawBytesHandlers = rawBytesHandlers;
      return this;
   }

   public KeyManagerBuilder keyManager() {
      return keyManager;
   }

   public TrustManagerBuilder trustManager() {
      return trustManager;
   }

   public HttpBuilder privatePools(boolean privatePools) {
      this.privatePools = privatePools;
      return this;
   }


   public void prepareBuild() {
   }

   public Http build(boolean isDefault) {
      if (http != null) {
         if (isDefault != http.isDefault()) {
            throw new IllegalArgumentException("Already built as isDefault=" + http.isDefault());
         }
         return http;
      }
      List<HttpVersion> httpVersions = new ArrayList<>();
      // The order is important here because it will be provided to the ALPN
      if (allowHttp2) {
         httpVersions.add(HttpVersion.HTTP_2_0);
      }
      if (allowHttp1x) {
         httpVersions.add(HttpVersion.HTTP_1_1);
         httpVersions.add(HttpVersion.HTTP_1_0);
      }
      if (directHttp2) {
         throw new UnsupportedOperationException("Direct HTTP/2 not implemented");
      }
      Protocol protocol = this.protocol != null ? this.protocol : Protocol.fromPort(port);
      return http = new Http(isDefault, protocol, host, protocol.portOrDefault(port), addresses.toArray(new String[0]),
            httpVersions.toArray(new HttpVersion[0]), maxHttp2Streams, pipeliningLimit,
            sharedConnections, directHttp2, requestTimeout, rawBytesHandlers, keyManager.build(), trustManager.build(),
            privatePools);
   }

   @Override
   public void readFrom(HttpBuilder other) {
      this.protocol = other.protocol;
      this.host = other.host;
      this.port = other.port;
      this.addresses = new ArrayList<>(addresses);
      this.allowHttp1x = other.allowHttp1x;
      this.allowHttp2 = other.allowHttp2;
      this.sharedConnections = other.sharedConnections;
      this.maxHttp2Streams = other.maxHttp2Streams;
      this.pipeliningLimit = other.pipeliningLimit;
      this.directHttp2 = other.directHttp2;
      this.requestTimeout = other.requestTimeout;
      this.rawBytesHandlers = other.rawBytesHandlers;
      this.keyManager.readFrom(other.keyManager);
      this.trustManager.readFrom(other.trustManager);
   }

   public class KeyManagerBuilder implements Rewritable<KeyManagerBuilder> {
      private String storeType = "JKS";
      private byte[] storeBytes;
      private String password;
      private String alias;
      private byte[] certBytes;
      private byte[] keyBytes;

      public KeyManagerBuilder storeType(String type) {
         this.storeType = type;
         return this;
      }

      public KeyManagerBuilder storeFile(String filename) {
         try {
            this.storeBytes = readBytes(filename);
         } catch (IOException e) {
            throw new BenchmarkDefinitionException("Cannot read key store file " + filename, e);
         }
         return this;
      }

      public KeyManagerBuilder storeBytes(byte[] storeBytes) {
         this.storeBytes = storeBytes;
         return this;
      }

      public KeyManagerBuilder password(String password) {
         this.password = password;
         return this;
      }

      public KeyManagerBuilder alias(String alias) {
         this.alias = alias;
         return this;
      }

      public KeyManagerBuilder certFile(String certFile) {
         try {
            this.certBytes = readBytes(certFile);
         } catch (IOException e) {
            throw new BenchmarkDefinitionException("Cannot read certificate file " + certFile, e);
         }
         return this;
      }

      public KeyManagerBuilder certBytes(byte[] certBytes) {
         this.certBytes = certBytes;
         return this;
      }

      public KeyManagerBuilder keyFile(String keyFile) {
         try {
            this.keyBytes = readBytes(keyFile);
         } catch (IOException e) {
            throw new BenchmarkDefinitionException("Cannot read private key file " + keyFile, e);
         }
         return this;
      }

      public KeyManagerBuilder keyBytes(byte[] keyBytes) {
         this.keyBytes = keyBytes;
         return this;
      }

      public HttpBuilder end() {
         return HttpBuilder.this;
      }

      public Http.KeyManager build() {
         return new Http.KeyManager(storeType, storeBytes, password, alias, certBytes, keyBytes);
      }

      @Override
      public void readFrom(KeyManagerBuilder other) {
         this.storeType = other.storeType;
         this.storeBytes = other.storeBytes;
         this.password = other.password;
         this.alias = other.alias;
         this.certBytes = other.certBytes;
         this.keyBytes = other.keyBytes;
      }
   }

   public class TrustManagerBuilder implements Rewritable<TrustManagerBuilder> {
      private String storeType = "JKS";
      private byte[] storeBytes;
      private String password;
      private byte[] certBytes;

      public TrustManagerBuilder storeType(String type) {
         this.storeType = type;
         return this;
      }

      public TrustManagerBuilder storeFile(String filename) {
         try {
            this.storeBytes = readBytes(filename);
         } catch (IOException e) {
            throw new BenchmarkDefinitionException("Cannot read keystore file " + filename, e);
         }
         return this;
      }

      public TrustManagerBuilder storeBytes(byte[] storeBytes) {
         this.storeBytes = storeBytes;
         return this;
      }

      public TrustManagerBuilder password(String password) {
         this.password = password;
         return this;
      }

      public TrustManagerBuilder certFile(String certFile) {
         try {
            this.certBytes = readBytes(certFile);
         } catch (IOException e) {
            throw new BenchmarkDefinitionException("Cannot read certificate file " + certFile, e);
         }
         return this;
      }

      public TrustManagerBuilder certBytes(byte[] certBytes) {
         this.certBytes = certBytes;
         return this;
      }

      public HttpBuilder end() {
         return HttpBuilder.this;
      }

      public Http.TrustManager build() {
         return new Http.TrustManager(storeType, storeBytes, password, certBytes);
      }

      @Override
      public void readFrom(TrustManagerBuilder other) {
         this.storeType = other.storeType;
         this.storeBytes = other.storeBytes;
         this.password = other.password;
         this.certBytes = other.certBytes;
      }
   }

   private static byte[] readBytes(String filename) throws IOException {
      try (InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(filename)) {
         if (stream != null) {
            return Util.toByteArray(stream);
         }
      }
      return Files.readAllBytes(Paths.get(filename));
   }
}
