package http2.bench.jetty;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import http2.bench.Distribution;
import http2.bench.ServerCommandBase;
import http2.bench.servlet.ServletServer;
import org.eclipse.jetty.alpn.server.ALPNServerConnectionFactory;
import org.eclipse.jetty.http2.HTTP2Cipher;
import org.eclipse.jetty.http2.server.HTTP2ServerConnectionFactory;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;

/**
 * @author <a href="mailto:plopes@redhat.com">Paulo Lopes</a>
 */
@Parameters()
public class JettyServerCommand extends ServerCommandBase {

  private static final String STORE_PASSWORD = "password";

  @Parameter(names = "--async")
  public boolean async = false;

  public void run() throws Exception {

    File root = new File("jetty.uploads");
    root.mkdirs();

    // create config
    final HttpConfiguration config = new HttpConfiguration();
    config.setSecureScheme("https");
    config.setSecurePort(8443);
    config.setSendXPoweredBy(false);
    config.setSendServerVersion(false);
    config.addCustomizer(new SecureRequestCustomizer());

    HttpConnectionFactory httpFactory = new HttpConnectionFactory(config);
    HTTP2ServerConnectionFactory http2Factory = new HTTP2ServerConnectionFactory(config);
    ALPNServerConnectionFactory alpn = createAlpnProtocolFactory(httpFactory);
    Server server = createServer(httpFactory, http2Factory, alpn);

    ServletServer servlet = new ServletServer();
    servlet.setRoot(root);
    servlet.setAsync(async);
    servlet.setBackend(backend);
    servlet.setPoolSize(poolSize);
    servlet.setDelay(new Distribution(delay));
    servlet.setBackendHost(backendHost);
    servlet.setBackendPort(backendPort);

    servlet.doInit();

    server.setHandler(new AbstractHandler() {
      @Override
      public void handle(String s, Request req, HttpServletRequest hreq, HttpServletResponse hresp) throws IOException, ServletException {
        servlet.handle(hreq, hresp);
      }
    });

    server.start();
  }

  static String password(String name) {
    String pw = System.getProperty(name + ".password");
    return pw != null ? pw : STORE_PASSWORD;
  }


  private static ALPNServerConnectionFactory createAlpnProtocolFactory(HttpConnectionFactory httpConnectionFactory) {
    NegotiatingServerConnectionFactory.checkProtocolNegotiationAvailable();
    ALPNServerConnectionFactory alpn = new ALPNServerConnectionFactory();
    alpn.setDefaultProtocol(httpConnectionFactory.getProtocol());
    return alpn;
  }

  private static SslConnectionFactory prepareSsl(ALPNServerConnectionFactory alpn) {
    SslContextFactory sslContextFactory = new SslContextFactory();
    sslContextFactory.setKeyStorePath(JettyServerCommand.class.getResource("server.keystore").toExternalForm());
    sslContextFactory.setKeyStorePassword(password("server.keystore"));
    sslContextFactory.setCipherComparator(HTTP2Cipher.COMPARATOR);
    sslContextFactory.setUseCipherSuitesOrder(true);
    sslContextFactory.setIncludeCipherSuites("TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256");
    return new SslConnectionFactory(sslContextFactory, alpn.getProtocol());
  }

  private Server createServer(
      HttpConnectionFactory httpConnectionFactory,
      HTTP2ServerConnectionFactory http2ConnectionFactory,
      ALPNServerConnectionFactory alpn) {
    Server server = new Server(new QueuedThreadPool(200));
    ServerConnector connector = new ServerConnector(server, prepareSsl(alpn), alpn, http2ConnectionFactory, httpConnectionFactory);
    connector.setHost("0.0.0.0");
    connector.setPort(8443);
    connector.setAcceptQueueSize(soBacklog);
    server.addConnector(connector);
    return server;
  }
}
