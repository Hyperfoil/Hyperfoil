package http2.bench.servlet;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import http2.bench.Backend;

import javax.servlet.AsyncContext;
import javax.servlet.GenericServlet;
import javax.servlet.ReadListener;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class ServletServer extends GenericServlet {

  private Backend backend;
  private boolean async;
  private File root;
  private HikariDataSource ds;

  public Backend getBackend() {
    return backend;
  }

  public void setBackend(Backend backend) {
    this.backend = backend;
  }

  public boolean isAsync() {
    return async;
  }

  public void setAsync(boolean async) {
    this.async = async;
  }

  public File getRoot() {
    return root;
  }

  public void setRoot(File root) {
    this.root = root;
  }

  @Override
  public void init() throws ServletException {
    ServletConfig cfg = getServletConfig();
    backend = Backend.valueOf(cfg.getInitParameter("backend"));
    root = new File(cfg.getInitParameter("root"));
    async = Boolean.valueOf(cfg.getInitParameter("async"));
    int dbPoolSize = Integer.parseInt(cfg.getInitParameter("dbPoolSize"));
    root.mkdirs();

    if (backend == Backend.DB) {
      HikariConfig config = new HikariConfig();
      config.setJdbcUrl("jdbc:postgresql://localhost/testdb");
      config.setUsername("vertx");
      config.setPassword("password");
      config.addDataSourceProperty("cachePrepStmts", "true");
      config.addDataSourceProperty("prepStmtCacheSize", "250");
      config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
      config.setMaximumPoolSize(dbPoolSize);
      ds = new HikariDataSource(config);
      try (Connection conn = ds.getConnection()) {
        try (Statement statement = conn.createStatement()) {
          statement.execute("DROP TABLE IF EXISTS data_table");
          statement.execute("CREATE TABLE IF NOT EXISTS data_table (data json)");
        }
      } catch (Exception e) {
        throw new ServletException(e);
      }
    }
  }

  @Override
  public void service(ServletRequest sreq, ServletResponse sresp) throws ServletException, IOException {
    handle((HttpServletRequest) sreq, (HttpServletResponse) sresp);
  }

  public void handle(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    if (req.getMethod().equals("POST")) {
      BiConsumer<byte[],Integer> dst;
      switch (backend) {
        case DISK:
          File f = new File(root, UUID.randomUUID().toString());
          FileOutputStream out = new FileOutputStream(f);
          dst = (buf,len) -> {
            try {
              if (len != -1) {
                out.write(buf, 0, len);
              } else {
                out.close();
                f.delete();
              }
            } catch (IOException e) {
              e.printStackTrace();
            }
          };
          break;
        default:
          dst = (buf,len) -> {};
          break;
      }
      if (async) {
        handlePostAsync(dst, req, resp);
      } else {
        handlePost(dst, req, resp);
      }
    } else {
      if (backend == Backend.DB) {
        try (Connection conn = ds.getConnection()) {
          try (PreparedStatement statement = conn.prepareStatement("INSERT INTO data_table (data) VALUES (cast(? as json))")) {
            statement.setObject (1, "{\"some\":\"json\"}");
            statement.executeUpdate();
          }
        } catch (Exception e) {
          e.printStackTrace();
          resp.sendError(500);
          return;
        }
      }
      sendResponse(resp);
    }
  }

  private void handlePostAsync(BiConsumer<byte[], Integer> out, HttpServletRequest hreq, HttpServletResponse hresp) throws IOException {
    // http://fr.slideshare.net/SimoneBordet/servlet-31-async-io
    if (hreq.getAttribute(AsyncContext.class.getName()) == null) {
      AsyncContext context = hreq.startAsync();
      hreq.setAttribute(AsyncContext.class.getName(), context);
      ServletInputStream in = hreq.getInputStream();
      byte[] buffer = new byte[512];
      in.setReadListener(new ReadListener() {

        @Override
        public void onDataAvailable() throws IOException {
          try {
            while (in.isReady() && !in.isFinished()) {
              int len = in.read(buffer);
              if (len > 0) {
                out.accept(buffer, len);
              }
            }
          } catch (IOException e) {
            e.printStackTrace();
          }
        }

        @Override
        public void onAllDataRead() throws IOException {
          out.accept(null, -1);
          sendResponse((HttpServletResponse) context.getResponse());
          context.complete();
        }

        @Override
        public void onError(Throwable throwable) {
        }
      });
    }
  }

  private void handlePost(BiConsumer<byte[], Integer> out, HttpServletRequest hreq, HttpServletResponse hresp) throws IOException {
    try (ServletInputStream in = hreq.getInputStream()) {
      byte[] buffer = new byte[512];
      while (true) {
        int len = in.read(buffer, 0, buffer.length);
        out.accept(buffer, len);
        if (len == -1) {
          break;
        }
      }
      sendResponse(hresp);
    }
  }

  private void sendResponse(HttpServletResponse response) throws IOException {
    response.setContentType("text/plain");
    response.getOutputStream().write("Hello World".getBytes());
    response.getOutputStream().close();
  }
}
