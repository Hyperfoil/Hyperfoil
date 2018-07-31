package io.sailrocket.core.client.netty;

import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.SupportedCipherSuiteFilter;
import io.sailrocket.api.HttpClient;
import io.sailrocket.api.HttpMethod;
import io.sailrocket.api.HttpRequest;
import io.netty.buffer.Unpooled;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.vertx.core.http.HttpVersion;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
abstract class HttpClientImpl implements HttpClient {

    static HttpClient create(EventLoopGroup eventLoopGroup, HttpVersion protocol, boolean ssl, int size, int port, String host, int maxConcurrentStream) throws Exception {
        SslContext sslContext = null;
        if (ssl) {
            SslProvider provider = OpenSsl.isAlpnSupported() ? SslProvider.OPENSSL : SslProvider.JDK;
            SslContextBuilder builder = SslContextBuilder.forClient()
                    .sslProvider(provider)
                    /* NOTE: the cipher filter may not include all ciphers required by the HTTP/2 specification.
                     * Please refer to the HTTP/2 specification for cipher requirements. */
                    .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
                    .trustManager(InsecureTrustManagerFactory.INSTANCE);
            if (protocol == HttpVersion.HTTP_2) {
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
        if (protocol == HttpVersion.HTTP_2) {
            return new Http2Client(eventLoopGroup, sslContext, size, port, host, maxConcurrentStream);
        } else {
            return new Http1xClient(eventLoopGroup, sslContext, size, port, host, maxConcurrentStream);
        }
    }

    final int maxConcurrentStream;
    private final int size;
    final int port;
    final String host;
    final EventLoopGroup eventLoopGroup;
    private final EventLoop scheduler;
    final SslContext sslContext;
    //TODO: replace with fast connection pool
    private final ArrayList<HttpConnection> all = new ArrayList<>();
    private long index;
    private int count; // The estimated count : created + creating
    private Consumer<Void> startedHandler;
    private boolean shutdown;

    HttpClientImpl(EventLoopGroup eventLoopGroup, SslContext sslContext, int size, int port, String host, int maxConcurrentStream) {
        this.maxConcurrentStream = maxConcurrentStream;
        this.eventLoopGroup = eventLoopGroup;
        this.sslContext = sslContext;
        this.size = size;
        this.port = port;
        this.host = host;
        this.scheduler = eventLoopGroup.next();
    }

    public void start(Consumer<Void> completionHandler) {
        synchronized (this) {
            if (startedHandler != null) {
                throw new IllegalStateException();
            }
            startedHandler = completionHandler;
        }
        checkCreateConnections(0);
    }

    private synchronized void checkCreateConnections(int retry) {

        //TODO:: configurable
        if (retry > 100) {
            System.out.println("DANGER - handle me");
        }
        if (count < size) {
            count++;
            connect(port, host, (conn, err) -> {
                if (err == null) {
                    Consumer<Void> handler = null;
                    synchronized (HttpClientImpl.this) {
                        all.add(conn);
                        if (count < size) {
                            checkCreateConnections(0);
                        } else {
                            if (count() == size) {
                                handler = startedHandler;
                                startedHandler = null;
                            }
                        }
                    }

                    conn.context().channel().closeFuture().addListener(v -> {
                        synchronized (HttpClientImpl.this) {
                            count--;
                            all.remove(conn);
                        }
                        if (!shutdown) {
                            checkCreateConnections(0);
                        }
                    });

                    if (handler != null) {
                        handler.accept(null);
                    }
                } else {
                    synchronized (HttpClientImpl.this) {
                        count--;
                    }
                    checkCreateConnections(retry + 1);
                }
            });
            scheduler.schedule(() -> {
                checkCreateConnections(retry);
            }, 2, TimeUnit.MILLISECONDS);

        }
    }

    abstract void connect(int port, String host, BiConsumer<HttpConnection, Throwable> handler);

    public abstract long bytesRead();

    public abstract long bytesWritten();

    synchronized int count() {
        return all.size();
    }

    @Override
    public HttpRequest request(HttpMethod method, String path) {
        try {
            return choose().request(method, path);
        } catch (NoSuchElementException ex) {
            return null;
        }
    }

    //TODO:: delegate to a connection pool
    private synchronized HttpConnection choose() {
        return all.stream()
                .filter(con -> con.isAvailable())
                .findFirst()
                .get();
    }

    public abstract void resetStatistics();

    @Override
    public synchronized long inflight() {
        return all.stream().mapToInt(con -> con.inflight()).sum();
    }

    public void shutdown() {
        HashSet<HttpConnection> list;
        synchronized (this) {
            if (shutdown)
                return;
            shutdown = true;
            list = new HashSet<>(all);
        }
        list.forEach(conn -> {
            conn.context().writeAndFlush(Unpooled.EMPTY_BUFFER);
            conn.context().close();
            conn.context().flush();
        });
    }

}
