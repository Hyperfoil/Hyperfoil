package io.hyperfoil.core.session;

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
import io.hyperfoil.core.builders.HttpBuilder;
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
      scenario().initialSequence("test")
            .step(s -> {
               s.httpConnectionPool(null).connections().forEach(c -> {
                  try {
                     Field f = c.getClass().getDeclaredField("ctx");
                     f.setAccessible(true);
                     ChannelHandlerContext ctx = (ChannelHandlerContext) f.get(c);
                     ctx.pipeline().addFirst(new BufferingDecoder());
                  } catch (NoSuchFieldException e) {
                     throw new IllegalStateException();
                  } catch (IllegalAccessException e) {
                     throw new IllegalStateException();
                  }

               });
               return true;
            })
            .step().httpRequest(HttpMethod.GET).path("/test")
               .handler().rawBytesHandler((session, byteBuf) -> {
                  log.info("Received chunk {} bytes:\n{}", byteBuf.readableBytes(),
                        byteBuf.toString(byteBuf.readerIndex(), byteBuf.readableBytes(), StandardCharsets.UTF_8));
                  if (byteBuf.toString(StandardCharsets.UTF_8).indexOf(SHIBBOLETH) >= 0) {
                     throw new IllegalStateException();
                  }
                  rawBytesSeen.set(true);
               }).endHandler()
            .endStep()
            .step().httpRequest(HttpMethod.GET).path("/test2").endStep()
            .step().awaitAllResponses()
            .endSequence();

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
