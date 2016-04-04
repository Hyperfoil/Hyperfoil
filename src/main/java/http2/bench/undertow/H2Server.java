package http2.bench.undertow;

import http2.bench.Env;
import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.util.Headers;
import org.xnio.Options;
import org.xnio.Sequence;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class H2Server {

  private static final char[] STORE_PASSWORD = "password".toCharArray();

  public static void main(final String[] args) throws Exception {
    String bindAddress = System.getProperty("bind.address", "localhost");
    SSLContext sslContext = createSSLContext();
    Undertow server = Undertow.builder()
        .setSocketOption(Options.SSL_SUPPORTED_CIPHER_SUITES, Sequence.of("TLS-ECDHE-RSA-AES128-GCM-SHA256"))
        .setServerOption(UndertowOptions.ENABLE_HTTP2, true)
        .setServerOption(Options.WORKER_IO_THREADS, Env.numCore())
        .addHttpListener(8080, bindAddress)
        .addHttpsListener(8443, bindAddress, sslContext)
        .setHandler(exchange -> {
          exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
          exchange.getResponseSender().send("Hello World");
        }).build();
    server.start();
  }

  static char[] password(String name) {
    String pw = System.getProperty(name + ".password");
    return pw != null ? pw.toCharArray() : STORE_PASSWORD;
  }


  private static SSLContext createSSLContext() throws Exception {

    final InputStream stream = H2Server.class.getResourceAsStream("server.keystore");

    KeyStore keyStore = KeyStore.getInstance("JKS");
    try(InputStream is = stream) {
      keyStore.load(is, password("server.keystore"));
    }

    KeyManager[] keyManagers;
    KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    keyManagerFactory.init(keyStore, password("key"));
    keyManagers = keyManagerFactory.getKeyManagers();

    SSLContext sslContext;
    sslContext = SSLContext.getInstance("TLS");
    sslContext.init(keyManagers, null, null);

    return sslContext;
  }
}
