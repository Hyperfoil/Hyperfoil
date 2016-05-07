package http2.bench.jetty;

import com.beust.jcommander.Parameters;
import http2.bench.ServerBase;
import org.eclipse.jetty.alpn.server.ALPNServerConnectionFactory;
import org.eclipse.jetty.http2.HTTP2Cipher;
import org.eclipse.jetty.http2.server.HTTP2ServerConnectionFactory;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.UUID;

/**
 * @author <a href="mailto:plopes@redhat.com">Paulo Lopes</a>
 */
@Parameters()
public class JettyServer extends ServerBase {

  private static final String STORE_PASSWORD = "password";

  public void run() throws Exception {

    File root = new File("jetty.uploads");
    root.mkdirs();

    System.out.println("Upload root: " + root.getAbsolutePath());

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
        if (httpServletRequest.getMethod().equals("POST")) {
          try (ServletInputStream inputStream = httpServletRequest.getInputStream()) {

            File f = new File(root, UUID.randomUUID().toString());
            try (FileOutputStream out = new FileOutputStream(f)) {
              byte[] buffer = new byte[512];
              while (true) {
                int len = inputStream.readLine(buffer, 0, buffer.length);
                if (len == -1) {
                  break;
                }
                out.write(buffer, 0, len);
              }
            } finally {
              f.delete();
            }
          }
        }


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
    sslContextFactory.setKeyStorePath(JettyServer.class.getResource("server.keystore").toExternalForm());
    sslContextFactory.setKeyStorePassword(password("server.keystore"));
    sslContextFactory.setCipherComparator(HTTP2Cipher.COMPARATOR);
    sslContextFactory.setUseCipherSuitesOrder(true);
    sslContextFactory.setIncludeCipherSuites("TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256");
    return new SslConnectionFactory(sslContextFactory, alpn.getProtocol());
  }

  private static Server createServer(HttpConnectionFactory httpConnectionFactory, HTTP2ServerConnectionFactory http2ConnectionFactory, ALPNServerConnectionFactory alpn) {
    Server server = new Server(new QueuedThreadPool(200));

//    server.setRequestLog(new AsyncNCSARequestLog());

    ServerConnector connector = new ServerConnector(server, prepareSsl(alpn), alpn, http2ConnectionFactory, httpConnectionFactory);
    connector.setPort(8443);
    server.addConnector(connector);

    return server;
  }
}
