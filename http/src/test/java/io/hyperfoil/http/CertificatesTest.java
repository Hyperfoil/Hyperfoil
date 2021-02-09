package io.hyperfoil.http;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import javax.net.ssl.SSLException;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.hyperfoil.http.config.HttpBuilder;
import io.hyperfoil.http.config.Protocol;
import io.hyperfoil.http.api.HttpClientPool;
import io.hyperfoil.http.api.HttpRequest;
import io.hyperfoil.http.api.HttpMethod;
import io.hyperfoil.http.api.HttpResponseHandlers;
import io.hyperfoil.api.session.SequenceInstance;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.statistics.Statistics;
import io.hyperfoil.http.connection.HttpClientPoolImpl;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.http.steps.HttpResponseHandlersImpl;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.ClientAuth;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.JksOptions;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class CertificatesTest {
   @Test
   public void testTrustJks(TestContext context) {
      test(context, false, server -> executeRequestAndStop(context, server,
            builder -> builder.trustManager().storeFile("keystore.jks").password("test123")));
   }

   @Test
   public void testTrustCert(TestContext context) {
      test(context, false, server -> executeRequestAndStop(context, server,
            builder -> builder.trustManager().certFile("servercert.crt")));
   }

   @Test
   public void testTrustBadCert(TestContext context) {
      test(context, false, server -> {
         try {
            HttpClientPool client = client(server.actualPort(), builder -> builder.trustManager().certFile("badcert.pem"));
            client.start(context.asyncAssertFailure());
         } catch (SSLException e) {
            context.fail(e);
         }
      });
   }

   @Test
   public void testTrustBadJks(TestContext context) {
      test(context, false, server -> {
         try {
            HttpClientPool client = client(server.actualPort(), builder -> builder.trustManager().storeFile("bad.jks"));
            client.start(context.asyncAssertFailure());
         } catch (SSLException e) {
            context.fail(e);
         }
      });
   }

   @Test
   public void testClientJks(TestContext context) {
      test(context, true, server -> executeRequestAndStop(context, server,
            builder -> builder
                  .trustManager().storeFile("keystore.jks").password("test123").end()
                  .keyManager().storeFile("client.jks").password("test123")));
   }

   @Test
   public void testClientBadJks(TestContext context) {
      test(context, true, server -> {
         try {
            HttpClientPool client = client(server.actualPort(), builder -> builder
                  .trustManager().storeFile("keystore.jks").password("test123").end()
                  .keyManager().storeFile("bad.jks").password("test123"));
            client.start(context.asyncAssertFailure());
         } catch (SSLException e) {
            context.fail(e);
         }
      });
   }

   @Test
   public void testClientCertAndKey(TestContext context) {
      test(context, true, server -> executeRequestAndStop(context, server,
            builder -> builder
                  .trustManager().storeFile("keystore.jks").password("test123").end()
                  .keyManager().certFile("clientcert.pem").keyFile("clientkey.pem").password("test123")));
   }

   private void test(TestContext context, boolean requireClientTrust, Handler<HttpServer> handler) {
      HttpServerOptions serverOptions = new HttpServerOptions().setSsl(true)
            .setKeyStoreOptions(new JksOptions().setPath("keystore.jks").setPassword("test123"));
      if (requireClientTrust) {
         serverOptions.setClientAuth(ClientAuth.REQUIRED);
         serverOptions.setTrustStoreOptions(new JksOptions().setPath("client.jks").setPassword("test123"));
      }
      Vertx.vertx().createHttpServer(serverOptions).requestHandler(ctx -> ctx.response().end())
            .listen(0, "localhost", context.asyncAssertSuccess(handler));
   }

   private void executeRequestAndStop(TestContext context, HttpServer server, Consumer<HttpBuilder> configuration) {
      try {
         HttpClientPool client = client(server.actualPort(), configuration);
         Async async = context.async();
         client.start(context.asyncAssertSuccess(nil -> {
            Session session = SessionFactory.forTesting();
            HttpRunData.initForTesting(session);
            HttpRequest request = HttpRequestPool.get(session).acquire();
            AtomicBoolean statusReceived = new AtomicBoolean(false);
            HttpResponseHandlers handlers = HttpResponseHandlersImpl.Builder.forTesting()
                  .status((r, status) -> {
                     if (status != 200) {
                        context.fail("Unexpected status " + status);
                     } else {
                        statusReceived.set(true);
                     }
                  })
                  .onCompletion(s -> {
                     client.shutdown();
                     server.close();
                     if (statusReceived.get()) {
                        async.complete();
                     } else {
                        context.fail("Status was not received.");
                     }
                  }).build();
            request.method = HttpMethod.GET;
            request.path = "/ping";
            request.start(handlers, new SequenceInstance(), new Statistics(System.currentTimeMillis()));

            client.next().request(request, null, true, null, false);
         }));
      } catch (SSLException e) {
         server.close();
         context.fail(e);
      }
   }

   private HttpClientPool client(int port, Consumer<HttpBuilder> configuration) throws SSLException {
      HttpBuilder builder = HttpBuilder.forTesting()
            .protocol(Protocol.HTTPS).host("localhost").port(port);
      configuration.accept(builder);
      return HttpClientPoolImpl.forTesting(builder.build(true), 1);
   }

}
