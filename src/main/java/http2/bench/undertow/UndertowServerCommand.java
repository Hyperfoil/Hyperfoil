package http2.bench.undertow;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import http2.bench.ServerCommandBase;
import http2.bench.servlet.ServletServer;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.server.HttpHandler;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
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
public class UndertowServerCommand extends ServerCommandBase {

  private static final char[] STORE_PASSWORD = "password".toCharArray();

  @Parameter(names = "--io-threads")
  public int ioThreads = Runtime.getRuntime().availableProcessors();

  @Parameter(names = "--worker-threads")
  public int workerThreads = 64;

  @Parameter(names = "--servlet")
  public boolean servlet;

  @Parameter(names = "--async")
  public boolean async = false;

  public void run() throws Exception {
    String bindAddress = System.getProperty("bind.address", "localhost");
    SSLContext sslContext = createSSLContext();
    HttpHandler handler;
    if (servlet) {
      DeploymentInfo servletBuilder = Servlets.deployment()
          .setClassLoader(UndertowServerCommand.class.getClassLoader())
          .setContextPath("/")
          .setDeploymentName("test.war")
          .addServlets(Servlets.servlet("ServletServer", ServletServer.class).
              addMapping("/").
              setAsyncSupported(true).
              addInitParam("root", "undertow.uploads").
              addInitParam("async", "" + async).
              addInitParam("backend", backend.name()));
      DeploymentManager manager = Servlets.defaultContainer().addDeployment(servletBuilder);
      manager.deploy();
      handler = Handlers.path(Handlers.redirect("/")).addPrefixPath("/", manager.start());
    } else {
      handler = exchange -> {
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
        exchange.getResponseSender().send("Hello World");
      };
    }
    Undertow server = Undertow.builder()
        .setSocketOption(Options.SSL_SUPPORTED_CIPHER_SUITES, Sequence.of("TLS-ECDHE-RSA-AES128-GCM-SHA256"))
        .setSocketOption(Options.BACKLOG, acceptBacklog)
        .setServerOption(UndertowOptions.ENABLE_HTTP2, true)
        .setWorkerThreads(workerThreads)
        .setIoThreads(ioThreads)
        .addHttpsListener(httpsPort, bindAddress, sslContext)
        .setHandler(handler).build();
    server.start();
  }

  static char[] password(String name) {
    String pw = System.getProperty(name + ".password");
    return pw != null ? pw.toCharArray() : STORE_PASSWORD;
  }

  private static SSLContext createSSLContext() throws Exception {

    final InputStream stream = UndertowServerCommand.class.getResourceAsStream("server.keystore");

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
