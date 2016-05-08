package http2.bench.jetty;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import http2.bench.ServerBase;
import org.eclipse.jetty.alpn.server.ALPNServerConnectionFactory;
import org.eclipse.jetty.http2.HTTP2Cipher;
import org.eclipse.jetty.http2.server.HTTP2ServerConnectionFactory;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

import javax.servlet.AsyncContext;
import javax.servlet.ReadListener;
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

  @Parameter(names = "--blocking")
  public boolean blocking = false;

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
      public void handle(String s, Request req, HttpServletRequest hreq, HttpServletResponse hresp) throws IOException, ServletException {
        if (hreq.getMethod().equals("POST")) {
          File f = new File(root, UUID.randomUUID().toString());
          if (blocking) {
            handlePostBlocking(f, hreq, hresp);
          } else {
            handlePostNonBlocking(f, hreq, hresp);
          }
        } else {
          sendResponse(hresp);
        }
      }
    });

    server.start();
  }

  private void handlePostNonBlocking(File f, HttpServletRequest hreq, HttpServletResponse hresp) throws IOException {
    AsyncContext context = (AsyncContext) hreq.getAttribute(AsyncContext.class.getName());
    if (context == null) {
      context = hreq.startAsync();
      hreq.setAttribute(AsyncContext.class.getName(), context);
      ServletInputStream in = hreq.getInputStream();
      byte[] buffer = new byte[512];
      FileOutputStream out = new FileOutputStream(f);
      in.setReadListener(new ReadListener() {
        @Override
        public void onDataAvailable() throws IOException {
          try {
            while (in.isReady() && !in.isFinished()) {
              int len = in.read(buffer);
              if (len > 0) {
                out.write(buffer, 0, len);
              }
            }
          } catch (IOException e) {
            e.printStackTrace();
          }
        }

        @Override
        public void onAllDataRead() throws IOException {
          out.close();
          f.delete();
          sendResponse(hresp);
        }

        @Override
        public void onError(Throwable throwable) {
          throwable.printStackTrace();
          try {
            hresp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
          } catch (IOException ignore) {
          }
        }
      });
    }
  }

  private void handlePostBlocking(File f, HttpServletRequest hreq, HttpServletResponse hresp) throws IOException {
    try (ServletInputStream in = hreq.getInputStream()) {
      try (FileOutputStream out = new FileOutputStream(f)) {
        byte[] buffer = new byte[512];
        while (true) {
          int len = in.read(buffer, 0, buffer.length);
          if (len == -1) {
            break;
          }
          out.write(buffer, 0, len);
        }
      } finally {
        f.delete();
      }
      sendResponse(hresp);
    }
  }

  private void sendResponse(HttpServletResponse response) throws IOException {
    response.setContentType("text/plain");
    response.getOutputStream().write("Hello World".getBytes());
    response.getOutputStream().close();
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
