package http2.bench.undertow;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import http2.bench.ServerCommandBase;
import http2.bench.servlet.ServletServer;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.server.HttpHandler;
import io.undertow.server.protocol.http2.Http2UpgradeHandler;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.vertx.core.json.JsonArray;
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

  @Parameter(names = "--async")
  public boolean async = false;

  public void run() throws Exception {
    SSLContext sslContext = clearText ? null : createSSLContext();
    HttpHandler handler;
    JsonArray delayParam = new JsonArray();
    delay.forEach(delayParam::add);
    DeploymentInfo servletBuilder = Servlets.deployment()
        .setClassLoader(UndertowServerCommand.class.getClassLoader())
        .setContextPath("/")
        .setDeploymentName("test.war")
        .addServlets(Servlets.servlet("ServletServer", ServletServer.class).
            addMapping("/").
            setAsyncSupported(true).
            addInitParam("root", "undertow.uploads").
            addInitParam("async", "" + async).
            addInitParam("poolSize", "" + poolSize).
            addInitParam("delay", "" + delayParam.encode()).
            addInitParam("backendHost", "" + backendHost).
            addInitParam("backendPort", "" + backendPort).
            addInitParam("backend", backend.name()));
    DeploymentManager manager = Servlets.defaultContainer().addDeployment(servletBuilder);
    manager.deploy();
    handler = Handlers.path(Handlers.redirect("/")).addPrefixPath("/", manager.start());
    Undertow.Builder builder = Undertow.builder()
        .setSocketOption(Options.SSL_SUPPORTED_CIPHER_SUITES, Sequence.of("TLS-ECDHE-RSA-AES128-GCM-SHA256"))
        .setSocketOption(Options.BACKLOG, soBacklog)
        .setServerOption(UndertowOptions.ENABLE_HTTP2, true)
        .setWorkerThreads(workerThreads)
        .setIoThreads(ioThreads);
    if (clearText) {
      builder.addHttpListener(port, "0.0.0.0").setHandler(new Http2UpgradeHandler(handler));
    } else {
      builder.addHttpsListener(port, "0.0.0.0", sslContext).setHandler(handler);
    }
    Undertow server = builder.build();
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
