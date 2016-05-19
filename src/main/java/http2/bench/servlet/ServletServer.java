package http2.bench.servlet;

import com.squareup.okhttp.ConnectionPool;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import feign.Feign;
import http2.bench.Backend;
import http2.bench.microservice.MicroService;

import javax.servlet.AsyncContext;
import javax.servlet.GenericServlet;
import javax.servlet.ReadListener;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class ServletServer extends GenericServlet {

  private Backend backend;
  private boolean async;
  private File root;
  private int poolSize;
  private HikariDataSource ds;
  private int sleepTime;
  private MicroService microService;

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

  public int getPoolSize() {
    return poolSize;
  }

  public void setPoolSize(int poolSize) {
    this.poolSize = poolSize;
  }

  public int getSleepTime() {
    return sleepTime;
  }

  public void setSleepTime(int sleepTime) {
    this.sleepTime = sleepTime;
  }

  @Override
  public void init() throws ServletException {
    ServletConfig cfg = getServletConfig();
    backend = Backend.valueOf(cfg.getInitParameter("backend"));
    root = new File(cfg.getInitParameter("root"));
    async = Boolean.valueOf(cfg.getInitParameter("async"));
    poolSize = Integer.parseInt(cfg.getInitParameter("poolSize"));
    sleepTime = Integer.parseInt(cfg.getInitParameter("sleepTime"));
    try {
      doInit();
    } catch (Exception e) {
      throw new ServletException(e);
    }
  }

  public void doInit() throws Exception {
    if (backend == Backend.DISK) {
      root.mkdirs();
    } else if (backend == Backend.DB) {
      HikariConfig config = new HikariConfig();
      config.setJdbcUrl("jdbc:postgresql://localhost/testdb");
      config.setUsername("vertx");
      config.setPassword("password");
      config.addDataSourceProperty("cachePrepStmts", "true");
      config.addDataSourceProperty("prepStmtCacheSize", "250");
      config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
      config.setMaximumPoolSize(poolSize);
      ds = new HikariDataSource(config);
      try (Connection conn = ds.getConnection()) {
        try (Statement statement = conn.createStatement()) {
          statement.execute("DROP TABLE IF EXISTS data_table");
          statement.execute("CREATE TABLE IF NOT EXISTS data_table (data text)");
        }
      }
    } else if (backend == Backend.MICROSERVICE) {
      com.squareup.okhttp.OkHttpClient okHttpClient = new com.squareup.okhttp.OkHttpClient().setConnectionPool(new ConnectionPool(poolSize, 20));
      microService = Feign.builder().client(
          new feign.okhttp.OkHttpClient(okHttpClient)
      ).target(MicroService.class, "http://localhost:8080");
    }
  }

  @Override
  public void service(ServletRequest sreq, ServletResponse sresp) throws ServletException, IOException {
    handle((HttpServletRequest) sreq, (HttpServletResponse) sresp);
  }

  interface Output {
    void write(byte[] buff, int len) throws Exception;
  }

  public void handle(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    if (req.getMethod().equals("POST")) {
      Output dst;
      switch (backend) {
        case DISK: {
          File f = new File(root, UUID.randomUUID().toString());
          FileOutputStream out = new FileOutputStream(f);
          dst = (buf,len) -> {
            if (len != -1) {
              out.write(buf, 0, len);
            } else {
              out.close();
              f.delete();
            }
          };
          break;
        }
        case DB: {
          ByteArrayOutputStream buffer = new ByteArrayOutputStream();
          dst = (buf,len) -> {
            if (len != -1) {
              buffer.write(buf, 0, len);
            } else {
              buffer.close();
              try (Connection conn = ds.getConnection()) {
                try (PreparedStatement statement = conn.prepareStatement("INSERT INTO data_table (data) VALUES (?)")) {
                  statement.setObject (1, buffer.toString());
                  statement.executeUpdate();
                }
              }
            }
          };
          break;
        }
        case MICROSERVICE: {
          ByteArrayOutputStream buffer = new ByteArrayOutputStream();
          dst = (buf,len) -> {
            if (len != -1) {
              buffer.write(buf, 0, len);
            } else {
              buffer.close();
              microService.doPost(buffer.toByteArray());
            }
          };
          break;
        }
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
          try (PreparedStatement statement = conn.prepareStatement("SELECT pg_sleep(0.040)")) {
            try (ResultSet rs = statement.executeQuery()) {
              // Nothing to do
            }
          }
        } catch (Exception e) {
          e.printStackTrace();
          resp.sendError(500);
          return;
        }
      } else if (backend == Backend.MICROSERVICE) {
        microService.doGet();
      }
      sendResponse(resp);
    }
  }

  private void handlePostAsync(Output out, HttpServletRequest hreq, HttpServletResponse hresp) throws IOException {
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
                out.write(buffer, len);
              }
            }
          } catch (Exception e) {
            ((HttpServletResponse)context.getResponse()).sendError(500);
            context.complete();
          }
        }

        @Override
        public void onAllDataRead() throws IOException {
          try {
            out.write(null, -1);
            sendResponse((HttpServletResponse) context.getResponse());
          } catch (Exception e) {
            ((HttpServletResponse)context.getResponse()).sendError(500);
          }
          context.complete();
        }

        @Override
        public void onError(Throwable throwable) {
        }
      });
    }
  }

  private void handlePost(Output out, HttpServletRequest hreq, HttpServletResponse hresp) throws IOException {
    try (ServletInputStream in = hreq.getInputStream()) {
      byte[] buffer = new byte[512];
      while (true) {
        int len = in.read(buffer, 0, buffer.length);
        try {
          out.write(buffer, len);
        } catch (Exception e) {
          hresp.sendError(500);
          return;
        }
        if (len == -1) {
          break;
        }
      }
      sendResponse(hresp);
    }
  }

  private void sendResponse(HttpServletResponse response) throws IOException {
    if (sleepTime > 0) {
      try {
        Thread.sleep(sleepTime);
      } catch (InterruptedException ignore) {
        Thread.currentThread().interrupt();
      }
    }
    response.setContentType("text/plain");
    try (ServletOutputStream out = response.getOutputStream()) {
      out.write("Hello World".getBytes());
    }
  }
}
