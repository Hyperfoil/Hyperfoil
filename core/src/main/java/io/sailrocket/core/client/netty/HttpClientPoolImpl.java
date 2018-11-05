package io.sailrocket.core.client.netty;

import io.netty.channel.EventLoop;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.SupportedCipherSuiteFilter;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.EventExecutorGroup;
import io.sailrocket.api.connection.HttpClientPool;
import io.sailrocket.api.connection.HttpConnectionPool;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.sailrocket.api.http.HttpVersion;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.StreamSupport;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
abstract class HttpClientPoolImpl implements HttpClientPool {
   private static final Logger log = LoggerFactory.getLogger(HttpClientPoolImpl.class);

   final int maxConcurrentStream;
   final int port;
   final String host;
   final String address;
   final EventLoopGroup eventLoopGroup;
   final SslContext sslContext;
   private final HttpConnectionPoolImpl[] children;
   private final AtomicInteger idx = new AtomicInteger();
   private final Supplier<HttpConnectionPool> nextSupplier;


   static HttpClientPool create(EventLoopGroup eventLoopGroup, HttpVersion version, boolean ssl, int size, int port, String host, int maxConcurrentStream) throws Exception {
        SslContext sslContext = null;
        if (ssl) {
            SslProvider provider = OpenSsl.isAlpnSupported() ? SslProvider.OPENSSL : SslProvider.JDK;
            SslContextBuilder builder = SslContextBuilder.forClient()
                    .sslProvider(provider)
                    /* NOTE: the cipher filter may not include all ciphers required by the HTTP/2 specification.
                     * Please refer to the HTTP/2 specification for cipher requirements. */
                    .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
                    .trustManager(InsecureTrustManagerFactory.INSTANCE);
            if (version == HttpVersion.HTTP_2_0) {
                builder.applicationProtocolConfig(new ApplicationProtocolConfig(
                        ApplicationProtocolConfig.Protocol.ALPN,
                        // NO_ADVERTISE is currently the only mode supported by both OpenSsl and JDK providers.
                        ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                        // ACCEPT is currently the only mode supported by both OpenSsl and JDK providers.
                        ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                        ApplicationProtocolNames.HTTP_2,
                        ApplicationProtocolNames.HTTP_1_1));
            }
            sslContext = builder
                    .build();
        }
        if (version == HttpVersion.HTTP_2_0) {
            return new Http2ClientPool(eventLoopGroup, sslContext, size, port, host, maxConcurrentStream);
        } else {
            return new Http1XClientPool(eventLoopGroup, sslContext, size, port, host, maxConcurrentStream);
        }
    }

    HttpClientPoolImpl(EventLoopGroup eventLoopGroup, SslContext sslContext, int size, int port, String host, int maxConcurrentStream) {
        this.maxConcurrentStream = maxConcurrentStream;
        this.eventLoopGroup = eventLoopGroup;
        this.sslContext = sslContext;
        this.port = port;
        this.host = host;
        this.address = (sslContext == null ? "http://" : "https://") + host + ":" + port;

        int numExecutors = (int) StreamSupport.stream(eventLoopGroup.spliterator(), false).count();
        this.children = new HttpConnectionPoolImpl[numExecutors];
        if (size < numExecutors) {
            log.warn("Connection pool size ({}) too small: the event loop has {} executors. Setting connection pool size to {}",
                  size, numExecutors, numExecutors);
            size = numExecutors;
        }
        Iterator<EventExecutor> iterator = eventLoopGroup.iterator();
        for (int i = 0; i < numExecutors; ++i) {
            assert iterator.hasNext();
            int childSize = (i + 1) * size / numExecutors - i * size / numExecutors;
            children[i] = new HttpConnectionPoolImpl(this, (EventLoop) iterator.next(), childSize);
        }

        if (Integer.bitCount(children.length) == 1) {
           int shift = 32 - Integer.numberOfLeadingZeros(children.length - 1);
           int mask = (1 << shift) - 1;
           nextSupplier = () -> children[idx.getAndIncrement() & mask];
        } else {
           nextSupplier = () -> children[idx.getAndIncrement() % children.length];
        }
    }

    @Override
    public void start(Runnable completionHandler) {
       AtomicInteger countDown = new AtomicInteger(children.length);
       for (HttpConnectionPoolImpl child : children) {
          child.start(() -> {
             if (countDown.decrementAndGet() == 0) {
                completionHandler.run();
             }
          });
       }
    }

    @Override
    public void shutdown() {
       for (HttpConnectionPoolImpl child : children) {
          child.shutdown();
       }
       eventLoopGroup.shutdownGracefully(0, 10, TimeUnit.SECONDS);
    }

    abstract void connect(final HttpConnectionPool pool, BiConsumer<HttpConnection, Throwable> handler);


    @Override
    public EventExecutorGroup executors() {
        return eventLoopGroup;
    }

    @Override
    public HttpConnectionPool next() {
       return nextSupplier.get();
    }

   @Override
   public String address() {
      return address;
   }

}
