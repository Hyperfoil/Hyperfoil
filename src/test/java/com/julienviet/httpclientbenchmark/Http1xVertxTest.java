package com.julienviet.httpclientbenchmark;

import http2.bench.client.HttpClientProvider;
import io.vertx.ext.unit.TestContext;
import org.junit.Before;

public class Http1xVertxTest extends Http1xTestBase {

  @Before
  public void before(TestContext ctx) {
    provider = HttpClientProvider.vertx;
    super.before(ctx);
  }
}
