package io.hyperfoil.http.connection;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.core.impl.ConnectionStatsConsumer;
import io.hyperfoil.core.util.Util;
import io.hyperfoil.http.config.ConnectionPoolConfig;
import io.hyperfoil.http.config.Http;
import io.hyperfoil.http.api.HttpVersion;
import io.hyperfoil.http.api.HttpClientPool;
import io.hyperfoil.http.api.HttpConnectionPool;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoop;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.SupportedCipherSuiteFilter;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.concurrent.EventExecutor;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class HttpClientPoolImpl implements HttpClientPool {
   private static final Logger log = LoggerFactory.getLogger(HttpClientPoolImpl.class);

   final Http http;
   final String[] addressHosts;
   final int[] addressPorts;
   final int port;
   final String host;
   final String scheme;
   final String authority;
   final byte[] authorityBytes;
   final SslContext sslContext;
   final boolean forceH2c;
   private final HttpConnectionPool[] children;
   private final AtomicInteger idx = new AtomicInteger();
   private final Supplier<HttpConnectionPool> nextSupplier;

   public static HttpClientPoolImpl forTesting(Http http, int threads) throws SSLException {
      NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup(threads);
      EventLoop[] executors = StreamSupport.stream(eventLoopGroup.spliterator(), false)
            .map(EventLoop.class::cast).toArray(EventLoop[]::new);
      return new HttpClientPoolImpl(http, executors, Benchmark.forTesting(), 0) {
         @Override
         public void shutdown() {
            super.shutdown();
            eventLoopGroup.shutdownGracefully(0, 1, TimeUnit.SECONDS);
         }
      };
   }

   public HttpClientPoolImpl(Http http, EventLoop[] executors, Benchmark benchmark, int agentId) throws SSLException {
      this.http = http;
      this.sslContext = http.protocol().secure() ? createSslContext() : null;
      this.host = http.host();
      this.port = http.port();
      this.scheme = sslContext == null ? "http" : "https";
      this.authority = host + ":" + port;
      this.authorityBytes = authority.getBytes(StandardCharsets.UTF_8);
      this.forceH2c = http.versions().length == 1 && http.versions()[0] == HttpVersion.HTTP_2_0;

      this.children = new HttpConnectionPool[executors.length];
      int coreConnections, maxConnections, bufferConnections;
      switch (http.connectionStrategy()) {
         case SHARED_POOL:
         case SESSION_POOLS:
            coreConnections = benchmark.slice(http.sharedConnections().core(), agentId);
            maxConnections = benchmark.slice(http.sharedConnections().max(), agentId);
            bufferConnections = benchmark.slice(http.sharedConnections().buffer(), agentId);
            boolean hadBuffer = http.sharedConnections().buffer() > 0;
            if (coreConnections < executors.length || maxConnections < executors.length ||
                  (hadBuffer && bufferConnections < executors.length)) {
               int prevCore = coreConnections;
               int prevMax = maxConnections;
               int prevBuffer = bufferConnections;
               coreConnections = Math.max(coreConnections, executors.length);
               maxConnections = Math.max(maxConnections, executors.length);
               bufferConnections = Math.max(bufferConnections, hadBuffer ? executors.length : 0);
               log.warn("Connection pool size (core {}, max {}, buffer {}) too small: the event loop has {} executors. " +
                           "Setting connection pool size to core {}, max {}, buffer {}",
                     prevCore, prevMax, prevBuffer, executors.length, coreConnections, maxConnections, bufferConnections);
            }
            log.info("Allocating {} connections (max {}, buffer {}) in {} executors to {}",
                  coreConnections, maxConnections, bufferConnections, executors.length,
                  http.protocol().scheme + "://" + authority);
            break;
         case OPEN_ON_REQUEST:
         case ALWAYS_NEW:
            coreConnections = 0;
            maxConnections = 0;
            bufferConnections = 0;
            break;
         default:
            throw new IllegalArgumentException("Unknow connection strategy " + http.connectionStrategy());
      }
      // This algorithm should be the same as session -> executor assignment to prevent blocking
      // in always(N) scenario with N connections
      int coreShare = coreConnections / executors.length;
      int maxShare = maxConnections / executors.length;
      int bufferShare = bufferConnections / executors.length;
      int coreRemainder = coreConnections - coreShare * executors.length;
      int maxRemainder = maxConnections - maxShare * executors.length;
      int bufferRemainder = bufferConnections - bufferShare * executors.length;
      for (int i = 0; i < executors.length; ++i) {
         if (maxConnections > 0) {
            int core = coreShare + (i < coreRemainder ? 1 : 0);
            int max = maxShare + (i < maxRemainder ? 1 : 0);
            int buffer = bufferShare + (i < bufferRemainder ? 1 : 0);
            children[i] = new SharedConnectionPool(this, executors[i], new ConnectionPoolConfig(core, max, buffer, http.sharedConnections().keepAliveTime()));
         } else {
            children[i] = new ConnectionAllocator(this, executors[i]);
         }
      }

      if (Integer.bitCount(children.length) == 1) {
         int shift = 32 - Integer.numberOfLeadingZeros(children.length - 1);
         int mask = (1 << shift) - 1;
         nextSupplier = () -> children[idx.getAndIncrement() & mask];
      } else {
         nextSupplier = () -> children[idx.getAndIncrement() % children.length];
      }

      addressHosts = new String[http.addresses().length];
      addressPorts = new int[http.addresses().length];
      String[] addresses = http.addresses();
      for (int i = 0; i < addresses.length; i++) {
         final String address = addresses[i];
         // This code must handle addresses in form ipv4address, ipv4address:port, [ipv6address]:port, ipv6address
         int bracketIndex = address.lastIndexOf(']');
         int firstColonIndex = address.indexOf(':');
         int lastColonIndex = address.lastIndexOf(':');
         if (lastColonIndex >= 0 && ((bracketIndex >= 0 && lastColonIndex > bracketIndex) || (bracketIndex < 0 && lastColonIndex == firstColonIndex))) {
            addressHosts[i] = address.substring(0, lastColonIndex);
            addressPorts[i] = (int) Util.parseLong(address, lastColonIndex + 1, address.length(), port);
         } else {
            addressHosts[i] = address;
            addressPorts[i] = port;
         }
      }
   }

   private SslContext createSslContext() throws SSLException {
      SslProvider provider = SslProvider.isAlpnSupported(SslProvider.OPENSSL) ? SslProvider.OPENSSL : SslProvider.JDK;
      TrustManagerFactory trustManagerFactory = createTrustManagerFactory();

      SslContextBuilder builder = SslContextBuilder.forClient()
            .sslProvider(provider)
            /* NOTE: the cipher filter may not include all ciphers required by the HTTP/2 specification.
             * Please refer to the HTTP/2 specification for cipher requirements. */
            .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
            .trustManager(trustManagerFactory)
            .keyManager(createKeyManagerFactory());
      builder.applicationProtocolConfig(new ApplicationProtocolConfig(
            ApplicationProtocolConfig.Protocol.ALPN,
            // NO_ADVERTISE is currently the only mode supported by both OpenSsl and JDK providers.
            ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
            // ACCEPT is currently the only mode supported by both OpenSsl and JDK providers.
            ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
            Stream.of(http.versions()).map(HttpVersion::protocolName).toArray(String[]::new)
      ));
      return builder.build();
   }

   private KeyManagerFactory createKeyManagerFactory() {
      Http.KeyManager config = http.keyManager();
      if (config.storeBytes() == null && config.certBytes() == null && config.keyBytes() == null) {
         return null;
      }
      try {
         KeyStore ks = KeyStore.getInstance(config.storeType());
         if (config.storeBytes() != null) {
            ks.load(new ByteArrayInputStream(config.storeBytes()), config.password() == null ? null : config.password().toCharArray());
            if (config.alias() != null) {
               if (ks.containsAlias(config.alias()) && ks.isKeyEntry(config.alias())) {
                  KeyStore.PasswordProtection password = new KeyStore.PasswordProtection(config.password().toCharArray());
                  KeyStore.Entry entry = ks.getEntry(config.alias(), password);
                  ks = KeyStore.getInstance(config.storeType());
                  ks.load(null);
                  ks.setEntry(config.alias(), entry, password);
               } else {
                  throw new BenchmarkDefinitionException("Store file " + config.storeBytes() + " does not contain any entry for alias " + config.alias());
               }
            }
         } else {
            ks.load(null, null);
         }
         if (config.certBytes() != null || config.keyBytes() != null) {
            if (config.certBytes() == null || config.keyBytes() == null) {
               throw new BenchmarkDefinitionException("You should provide both certificate and private key for " + http.host() + ":" + http.port());
            }
            ks.setKeyEntry(config.alias() == null ? "default" : config.alias(), toPrivateKey(config.keyBytes()),
                  config.password().toCharArray(), new Certificate[]{ loadCertificate(config.certBytes()) });
         }
         KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
         keyManagerFactory.init(ks, config.password().toCharArray());
         return keyManagerFactory;
      } catch (IOException | GeneralSecurityException e) {
         throw new BenchmarkDefinitionException("Cannot create key manager for " + http.host() + ":" + http.port(), e);
      }
   }

   private PrivateKey toPrivateKey(byte[] bytes) throws NoSuchAlgorithmException, InvalidKeySpecException {
      int pos = 0, lastPos = bytes.length - 1;
      // Truncate first and last lines and any newlines.
      while (pos < bytes.length && isWhite(bytes[pos])) ++pos;
      while (pos < bytes.length && bytes[pos] != '\n') ++pos;
      while (lastPos >= 0 && isWhite(bytes[lastPos])) --lastPos;
      while (lastPos >= 0 && bytes[lastPos] != '\n') --lastPos;
      ByteBuffer buffer = ByteBuffer.allocate(lastPos - pos);
      while (pos < lastPos) {
         if (!isWhite(bytes[pos])) buffer.put(bytes[pos]);
         ++pos;
      }
      buffer.flip();
      ByteBuffer rawBuffer = Base64.getDecoder().decode(buffer);
      byte[] decoded = new byte[rawBuffer.limit()];
      rawBuffer.get(decoded);

      PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decoded);
      KeyFactory keyFactory = KeyFactory.getInstance("RSA");
      return keyFactory.generatePrivate(keySpec);
   }

   private boolean isWhite(byte b) {
      return b == ' ' || b == '\n' || b == '\r';
   }

   private TrustManagerFactory createTrustManagerFactory() {
      Http.TrustManager config = http.trustManager();
      if (config.storeBytes() == null && config.certBytes() == null) {
         return InsecureTrustManagerFactory.INSTANCE;
      }
      try {
         KeyStore ks = KeyStore.getInstance(config.storeType());
         if (config.storeBytes() != null) {
            ks.load(new ByteArrayInputStream(config.storeBytes()), config.password() == null ? null : config.password().toCharArray());
         } else {
            ks.load(null, null);
         }
         if (config.certBytes() != null) {
            ks.setCertificateEntry("default", loadCertificate(config.certBytes()));
         }
         TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
         trustManagerFactory.init(ks);
         return trustManagerFactory;
      } catch (GeneralSecurityException | IOException e) {
         throw new BenchmarkDefinitionException("Cannot create trust manager for " + http.host() + ":" + http.port(), e);
      }
   }

   private static Certificate loadCertificate(byte[] bytes) throws CertificateException, IOException {
      CertificateFactory cf = CertificateFactory.getInstance("X.509");
      return cf.generateCertificate(new ByteArrayInputStream(bytes));
   }

   @Override
   public Http config() {
      return http;
   }

   @Override
   public void start(Handler<AsyncResult<Void>> completionHandler) {
      AtomicInteger countDown = new AtomicInteger(children.length);
      for (HttpConnectionPool child : children) {
         child.start(result -> {
            if (result.failed() || countDown.decrementAndGet() == 0) {
               if (result.failed()) {
                  shutdown();
               }
               completionHandler.handle(result);
            }
         });
      }
   }

   @Override
   public void shutdown() {
      for (HttpConnectionPool child : children) {
         child.shutdown();
      }
   }

   void connect(final HttpConnectionPool pool, ConnectionReceiver handler) {
      Bootstrap bootstrap = new Bootstrap();
      bootstrap.channel(NioSocketChannel.class);
      bootstrap.group(pool.executor());
      bootstrap.option(ChannelOption.SO_KEEPALIVE, true);
      bootstrap.option(ChannelOption.SO_REUSEADDR, true);

      bootstrap.handler(new HttpChannelInitializer(this, handler));

      String address = this.host;
      int port = this.port;
      if (addressHosts.length > 0) {
         int index = ThreadLocalRandom.current().nextInt(addressHosts.length);
         address = addressHosts[index];
         port = addressPorts[index];
      }

      ChannelFuture fut = bootstrap.connect(new InetSocketAddress(address, port));
      fut.addListener(handler);
   }

   @Override
   public HttpConnectionPool next() {
      return nextSupplier.get();
   }

   @Override
   public HttpConnectionPool connectionPool(EventExecutor executor) {
      for (HttpConnectionPool pool : children) {
         if (pool.executor() == executor) {
            return pool;
         }
      }
      throw new IllegalStateException();
   }

   @Override
   public String host() {
      return host;
   }

   @Override
   public String authority() {
      return authority;
   }

   @Override
   public byte[] authorityBytes() {
      return authorityBytes;
   }

   @Override
   public String scheme() {
      return scheme;
   }

   @Override
   public boolean isSecure() {
      return sslContext != null;
   }

   @Override
   public void visitConnectionStats(ConnectionStatsConsumer consumer) {
      for (var pool : children) {
         pool.visitConnectionStats(consumer);
      }
   }
}
