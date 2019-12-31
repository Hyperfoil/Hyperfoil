package io.hyperfoil.core.session;

import static io.hyperfoil.core.builders.StepCatalog.SC;
import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;
import org.junit.runner.RunWith;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.hyperfoil.api.http.HttpMethod;
import io.hyperfoil.api.config.HttpBuilder;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class ChunkedTransferTest extends BaseScenarioTest {

   public static final String SHIBBOLETH = "Shibboleth";

   @Override
   protected void initHttp(HttpBuilder http) {
      http.pipeliningLimit(2);
   }

   @Override
   protected void initRouter() {
      router.route("/test").handler(ctx -> {
         HttpServerResponse response = ctx.response();
         response.setChunked(true);
         response.write("Foo");
         response.write("Bar");
         response.putTrailer("Custom", "Trailer");
         response.end();
      });
      router.route("/test2").handler(ctx -> {
         ctx.response().end(SHIBBOLETH);
      });
   }

   @Test
   public void testChunkedTransfer() {
      AtomicBoolean rawBytesSeen = new AtomicBoolean(false);
      // @formatter:off
      scenario().initialSequence("test")
            .step(s -> {
               s.httpDestinations().getConnectionPool(null).connections().forEach(c -> {
                  try {
                     Field f = c.getClass().getDeclaredField("ctx");
                     f.setAccessible(true);
                     ChannelHandlerContext ctx = (ChannelHandlerContext) f.get(c);
                     ctx.pipeline().addFirst(new BufferingDecoder());
                  } catch (NoSuchFieldException | IllegalAccessException e) {
                     throw new IllegalStateException();
                  }

               });
               return true;
            })
            .step(SC).httpRequest(HttpMethod.GET).path("/test")
               .handler().rawBytes((session, byteBuf, offset, length, isLastPart) -> {
                  log.info("Received chunk {} bytes:\n{}", length,
                        byteBuf.toString(offset, length, StandardCharsets.UTF_8));
                  if (byteBuf.toString(StandardCharsets.UTF_8).contains(SHIBBOLETH)) {
                     throw new IllegalStateException();
                  }
                  rawBytesSeen.set(true);
               }).endHandler()
               .sync(false)
            .endStep()
            .step(SC).httpRequest(HttpMethod.GET)
               .path("/test2")
               .sync(false)
            .endStep()
            .step(SC).awaitAllResponses()
            .endSequence();
      // @formatter:on
      runScenario();
      assertThat(rawBytesSeen).isTrue();
   }

   private class BufferingDecoder extends ChannelInboundHandlerAdapter {
      CompositeByteBuf composite = null;
      boolean buffering = true;

      @Override
      public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
         if (buffering && msg instanceof ByteBuf) {
            ByteBuf buf = (ByteBuf) msg;
            if (composite == null) {
               composite = new CompositeByteBuf(buf.alloc(), buf.isDirect(), 2, buf);
            } else {
               composite.addComponent(true, buf);
            }
            if (composite.toString(StandardCharsets.UTF_8).contains(SHIBBOLETH)) {
               buffering = false;
               super.channelRead(ctx, composite);
            }
         } else {
            super.channelRead(ctx, msg);
         }
      }
   }
}
