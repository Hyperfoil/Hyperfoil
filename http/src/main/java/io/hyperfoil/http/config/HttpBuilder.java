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
import io.hyperfoil.api.config.BuilderBase;
import io.hyperfoil.http.api.HttpVersion;
import io.hyperfoil.impl.Util;

/**
 * @author <a href="mailto:stalep@gmail.com">Ståle Pedersen</a>
 */
public class HttpBuilder implements BuilderBase<HttpBuilder> {

   private final HttpPluginBuilder parent;
   private String name;
   private Http http;
   private String originalDestination;
   private Protocol protocol;
   private String host;
   private int port = -1;
   private List<String> addresses = new ArrayList<>();
   private boolean allowHttp1x = true;
   private boolean allowHttp2 = true;
   private ConnectionPoolConfig.Builder sharedConnections = new ConnectionPoolConfig.Builder(this);
   private int maxHttp2Streams = 100;
   private int pipeliningLimit = 1;
   private boolean directHttp2 = false;
   private long requestTimeout = 30000;
   private long sslHandshakeTimeout = 10000;
   private boolean rawBytesHandlers = true;
   private KeyManagerBuilder keyManager = new KeyManagerBuilder(this);
   private TrustManagerBuilder trustManager = new TrustManagerBuilder(this);
   private ConnectionStrategy connectionStrategy = ConnectionStrategy.SHARED_POOL;
   private boolean useHttpCache = false;

   public static HttpBuilder forTesting() {
      return new HttpBuilder(null);
   }

   public HttpBuilder(HttpPluginBuilder parent) {
      this.parent = parent;
   }

   public HttpBuilder name(String name) {
      this.name = name;
      return this;
   }

   String name() {
      return name;
   }

   String authority() {
      if (host() == null) {
         return null;
      } else if (port == -1) {
         return host();
      } else {
         return host() + ":" + portOrDefault();
      }
   }

   public HttpBuilder protocol(Protocol protocol) {
      if (this.protocol != null) {
         throw new BenchmarkDefinitionException("Duplicate 'protocol'");
      }
      this.protocol = protocol;
      return this;
   }

   public Protocol protocol() {
      return protocol;
   }

   public String host() {
      return host;
   }

   public int portOrDefault() {
      if (port != -1) {
         return port;
      } else if (protocol != null) {
         return protocol.portOrDefault(port);
      } else {
         throw new BenchmarkDefinitionException("No port nor protocol has been defined");
      }
   }

   public HttpBuilder host(String destination) {
      if (this.host != null) {
         throw new BenchmarkDefinitionException("Duplicate 'host'. Are you missing '-'s?");
      }
      URL result;
      String spec;
      int schemeEnd = destination.indexOf("://");
      if (schemeEnd < 0) {
         spec = "http://" + destination;
         originalDestination = destination;
      } else {
         spec = destination;
         originalDestination = destination.substring(schemeEnd + 3);
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
         throw new BenchmarkDefinitionException("Host must not contain any path: " + destination);
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
      this.sharedConnections.core(sharedConnections).max(sharedConnections).buffer(0).keepAliveTime(0);
      return this;
   }

   public ConnectionPoolConfig.Builder sharedConnections() {
      return this.sharedConnections;
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

   public HttpBuilder sslHandshakeTimeout(long sslHandshakeTimeout) {
      this.sslHandshakeTimeout = sslHandshakeTimeout;
      return this;
   }

   public HttpBuilder sslHandshakeTimeout(String sslHandshakeTimeout) {
      if ("none".equals(sslHandshakeTimeout)) {
         this.sslHandshakeTimeout = -1;
      } else {
         this.sslHandshakeTimeout = Util.parseToMillis(sslHandshakeTimeout);
      }
      return this;
   }

   public long sslHandshakeTimeout() {
      return sslHandshakeTimeout;
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

   public HttpBuilder connectionStrategy(ConnectionStrategy connectionStrategy) {
      this.connectionStrategy = connectionStrategy;
      return this;
   }

   public ConnectionStrategy connectionStrategy() {
      return connectionStrategy;
   }

   public HttpBuilder useHttpCache(boolean useHttpCache) {
      this.useHttpCache = useHttpCache;
      return this;
   }

   public boolean useHttpCache() {
      return useHttpCache;
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
      if (originalDestination == null) {
         originalDestination = host;
         if (port >= 0) {
            originalDestination += ":" + port;
         }
      }
      Protocol protocol = this.protocol != null ? this.protocol : Protocol.fromPort(port);
      return http = new Http(name, isDefault, originalDestination, protocol, host, protocol.portOrDefault(port),
            addresses.toArray(new String[0]), httpVersions.toArray(new HttpVersion[0]), maxHttp2Streams,
            pipeliningLimit, sharedConnections.build(), directHttp2, requestTimeout, sslHandshakeTimeout,
            rawBytesHandlers, keyManager.build(), trustManager.build(), connectionStrategy, useHttpCache);
   }

   public static class KeyManagerBuilder implements BuilderBase<KeyManagerBuilder> {
      private final HttpBuilder parent;
      private String storeType = "JKS";
      private byte[] storeBytes;
      private String password;
      private String alias;
      private byte[] certBytes;
      private byte[] keyBytes;

      public KeyManagerBuilder(HttpBuilder parent) {
         this.parent = parent;
      }

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
         return parent;
      }

      public Http.KeyManager build() {
         return new Http.KeyManager(storeType, storeBytes, password, alias, certBytes, keyBytes);
      }
   }

   public static class TrustManagerBuilder implements BuilderBase<TrustManagerBuilder> {
      private final HttpBuilder parent;
      private String storeType = "JKS";
      private byte[] storeBytes;
      private String password;
      private byte[] certBytes;

      public TrustManagerBuilder(HttpBuilder parent) {
         this.parent = parent;
      }

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
         return parent;
      }

      public Http.TrustManager build() {
         return new Http.TrustManager(storeType, storeBytes, password, certBytes);
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
