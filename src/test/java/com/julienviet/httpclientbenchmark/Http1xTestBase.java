package com.julienviet.httpclientbenchmark;

import http2.bench.client.HttpClientCommand;
import http2.bench.client.HttpClientProvider;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpVersion;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

@RunWith(VertxUnitRunner.class)
public abstract class Http1xTestBase {

  private volatile int count;
  private Vertx vertx;
  protected HttpClientProvider provider;

  @Before
  public void before(TestContext ctx) {
    count = 0;
    vertx = Vertx.vertx();
    vertx.createHttpServer().requestHandler(req -> {
      count++;
      req.response().end();
    }).listen(8080, "localhost", ctx.asyncAssertSuccess());
  }

  @After
  public void after(TestContext ctx) {
    vertx.close(ctx.asyncAssertSuccess());
  }

  @Test
  public void testNetty() throws Exception {
    HttpClientCommand cmd = new HttpClientCommand();
    cmd.provider = provider;
    cmd.connections = 5;
    cmd.threads = 4;
    cmd.uriParam = Arrays.asList("http://localhost:8080");
    cmd.durationParam = "5s";
    cmd.concurrency = 1;
    cmd.rates = Arrays.asList(1000);
    cmd.protocol = HttpVersion.HTTP_1_1;
    cmd.run();
    System.out.println("NUMBER OF REQUESTS " + count);
  }

}
