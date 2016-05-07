package http2.bench.undertow;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import http2.bench.ServerBase;
import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.util.Headers;
import org.xnio.Options;
import org.xnio.Sequence;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.InputStream;
import java.security.KeyStore;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@Parameters()
public class UndertowServer extends ServerBase {

  private static final char[] STORE_PASSWORD = "password".toCharArray();

  @Parameter(names = "--worker-size")
  public int workerSize = Runtime.getRuntime().availableProcessors();

  public void run() throws Exception {
    String bindAddress = System.getProperty("bind.address", "localhost");
    SSLContext sslContext = createSSLContext();
    Undertow server = Undertow.builder()
        .setSocketOption(Options.SSL_SUPPORTED_CIPHER_SUITES, Sequence.of("TLS-ECDHE-RSA-AES128-GCM-SHA256"))
        .setServerOption(UndertowOptions.ENABLE_HTTP2, true)
        .setServerOption(Options.WORKER_IO_THREADS, workerSize)
        .addHttpListener(httpPort, bindAddress)
        .addHttpsListener(httpsPort, bindAddress, sslContext)
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

    final InputStream stream = UndertowServer.class.getResourceAsStream("server.keystore");

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
