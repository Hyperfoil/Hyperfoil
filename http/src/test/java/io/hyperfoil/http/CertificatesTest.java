package io.hyperfoil.http;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import javax.net.ssl.SSLException;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.hyperfoil.http.api.HttpConnectionPool;
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
   public void testClientBadJksTls12(TestContext context) {
      // Due to https://github.com/vert-x3/wiki/wiki/4.4.0-Deprecations-and-breaking-changes#tls-10-and-tls-11-protocols-are-disabled-by-default
      // which have added TLSv1.3 to the list of enabled protocols, we need to explicitly disable it to save it be
      // used over TLSv1.2.
      // This is necessary to verify the expected behaviour for TLSv1.2 which expect an early handshake failure
      test(context, true, server -> {
         try {
            HttpClientPool client = client(server.actualPort(), builder -> builder
                  .trustManager().storeFile("keystore.jks").password("test123").end()
                  .keyManager().storeFile("bad.jks").password("test123"));
            client.start(context.asyncAssertFailure());
         } catch (SSLException e) {
            context.fail(e);
         }
      }, Set.of("TLSv1.2"));
   }

   @Test
   public void testClientBadJksTls13(TestContext context) {
      // Still related https://github.com/vert-x3/wiki/wiki/4.4.0-Deprecations-and-breaking-changes#tls-10-and-tls-11-protocols-are-disabled-by-default
      // Given that TLSv1.3 should be picked over TLSv1.2, we expect the handshake to NOT fail and only a later
      // failure to occur when the client tries to send a request.
      test(context, true, server -> {
         try {
            HttpClientPool client = client(server.actualPort(), builder -> builder
                    .trustManager().storeFile("keystore.jks").password("test123").end()
                    .keyManager().storeFile("bad.jks").password("test123"));
            client.start(context.asyncAssertSuccess(nil -> sendPingAndFailIfReceiveAnyStatus(context, server, client, context.async())));
         } catch (SSLException e) {
            context.fail(e);
         }
      }, Set.of("TLSv1.2", "TLSv1.3"));
   }

   @Test
   public void testClientCertAndKey(TestContext context) {
      test(context, true, server -> executeRequestAndStop(context, server,
            builder -> builder
                  .trustManager().storeFile("keystore.jks").password("test123").end()
                  .keyManager().certFile("clientcert.pem").keyFile("clientkey.pem").password("test123")));
   }

   private void test(TestContext context, boolean requireClientTrust, Handler<HttpServer> handler, Set<String> enabledSecureTransportProtocols) {
      HttpServerOptions serverOptions = new HttpServerOptions().setSsl(true)
              .setKeyStoreOptions(new JksOptions().setPath("keystore.jks").setPassword("test123"));
      if (requireClientTrust) {
         serverOptions.setClientAuth(ClientAuth.REQUIRED);
         if (enabledSecureTransportProtocols != null) {
            serverOptions.setEnabledSecureTransportProtocols(enabledSecureTransportProtocols);
         }
         serverOptions.setTrustStoreOptions(new JksOptions().setPath("client.jks").setPassword("test123"));
      }
      Vertx.vertx().createHttpServer(serverOptions).requestHandler(ctx -> ctx.response().end())
              .listen(0, "localhost", context.asyncAssertSuccess(handler));
   }

   private void test(TestContext context, boolean requireClientTrust, Handler<HttpServer> handler) {
      test(context, requireClientTrust, handler, null);
   }

   private void executeRequestAndStop(TestContext context, HttpServer server, Consumer<HttpBuilder> configuration) {
      try {
         HttpClientPool client = client(server.actualPort(), configuration);
         Async async = context.async();
         client.start(context.asyncAssertSuccess(nil -> sendPingAndReceiveStatus(context, server, client, async, 200)));
      } catch (SSLException e) {
         server.close();
         context.fail(e);
      }
   }

   private static void sendPingAndFailIfReceiveAnyStatus(TestContext context, HttpServer server, HttpClientPool client, Async async) {
      sendPingAndReceiveStatus(context, server, client, async, null);
   }

   private static void sendPingAndReceiveStatus(TestContext context, HttpServer server, HttpClientPool client, Async async, Integer expectedStatus) {
      Session session = SessionFactory.forTesting();
      HttpRunData.initForTesting(session);
      HttpRequest request = HttpRequestPool.get(session).acquire();
      AtomicBoolean statusReceived = new AtomicBoolean(false);
      HttpResponseHandlers handlers = HttpResponseHandlersImpl.Builder.forTesting()
            .status((r, status) -> {
               if (expectedStatus == null || status != expectedStatus) {
                  context.fail("Unexpected status " + status);
               } else {
                  statusReceived.set(true);
               }
            })
            .onCompletion(s -> {
               client.shutdown();
               server.close();
               if (statusReceived.get() || expectedStatus == null) {
                  async.complete();
               } else {
                  context.fail("Status was not received.");
               }
            }).build();
      request.method = HttpMethod.GET;
      request.path = "/ping";

      HttpConnectionPool pool = client.next();
      request.start(pool, handlers, new SequenceInstance(), new Statistics(System.currentTimeMillis()));
      pool.acquire(false, c -> request.send(c, null, true, null));
   }

   private HttpClientPool client(int port, Consumer<HttpBuilder> configuration) throws SSLException {
      HttpBuilder builder = HttpBuilder.forTesting()
            .protocol(Protocol.HTTPS).host("localhost").port(port);
      configuration.accept(builder);
      return HttpClientPoolImpl.forTesting(builder.build(true), 1);
   }

}
