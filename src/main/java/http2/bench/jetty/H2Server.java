package http2.bench.jetty;

import org.eclipse.jetty.alpn.server.ALPNServerConnectionFactory;
import org.eclipse.jetty.http2.HTTP2Cipher;
import org.eclipse.jetty.http2.server.HTTP2ServerConnectionFactory;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @author <a href="mailto:plopes@redhat.com">Paulo Lopes</a>
 */
public class H2Server {

  private static final String STORE_PASSWORD = "password";

  public static void main(final String[] args) throws Exception {
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

    server.setHandler(new AbstractHandler() {
      @Override
      public void handle(String s, Request req, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws IOException, ServletException {

        final Response res = req.getResponse();

        res.setContentType("text/plain");
        res.getOutputStream().write("Hello World".getBytes());
        res.getOutputStream().close();
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
    sslContextFactory.setKeyStorePath(H2Server.class.getResource("server.keystore").toExternalForm());
    sslContextFactory.setKeyStorePassword(password("server.keystore"));
    sslContextFactory.setCipherComparator(HTTP2Cipher.COMPARATOR);
    sslContextFactory.setUseCipherSuitesOrder(true);
    sslContextFactory.setIncludeCipherSuites("TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256");
    return new SslConnectionFactory(sslContextFactory, alpn.getProtocol());
  }

  private static Server createServer(HttpConnectionFactory httpConnectionFactory, HTTP2ServerConnectionFactory http2ConnectionFactory, ALPNServerConnectionFactory alpn) {
    Server server = new Server();
//    server.setRequestLog(new AsyncNCSARequestLog());

    ServerConnector connector = new ServerConnector(server, prepareSsl(alpn), alpn, http2ConnectionFactory, httpConnectionFactory);
    connector.setPort(8443);
    server.addConnector(connector);

    return server;
  }
}
