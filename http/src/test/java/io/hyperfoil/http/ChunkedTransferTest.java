package io.hyperfoil.http;

import static io.hyperfoil.http.steps.HttpStepCatalog.SC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import io.hyperfoil.api.config.BaseSequenceBuilder;
import io.hyperfoil.api.config.Locator;
import io.hyperfoil.api.connection.Request;
import io.hyperfoil.api.processor.RawBytesHandler;
import io.hyperfoil.core.data.DataFormat;
import io.hyperfoil.core.handlers.StoreProcessor;
import io.hyperfoil.core.handlers.json.JsonHandler;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.core.test.TestUtil;
import io.hyperfoil.http.api.HttpConnection;
import io.hyperfoil.http.api.HttpDestinationTable;
import io.hyperfoil.http.api.HttpMethod;
import io.hyperfoil.http.config.HttpBuilder;
import io.hyperfoil.impl.Util;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.vertx.core.http.HttpServerResponse;

public class ChunkedTransferTest extends BaseHttpScenarioTest {

   public static final String SHIBBOLETH = "Shibboleth";
   private static final int FOO_VALUE_SIZE = 10 * 1024 * 1024;
   private String fooValue;

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
      router.route("/test3").handler(ctx -> {
         HttpServerResponse response = ctx.response().setChunked(true);
         ThreadLocalRandom rand = ThreadLocalRandom.current();
         for (int i = 0; i < 3; ++i) {
            response.write(TestUtil.randomString(rand, 100));
         }
         response.end();
      });
      router.route("/test4").handler(ctx -> {
         if (fooValue == null) {
            final byte[] hugeValue = new byte[FOO_VALUE_SIZE];
            Arrays.fill(hugeValue, (byte) 'a');
            final String hugeValueString = new String(hugeValue, 0, 0, hugeValue.length);
            fooValue = hugeValueString;
         }
         HttpServerResponse response = ctx.response().setChunked(true);
         response.write("{");
         // many useless crap ones
         for (int i = 0; i < 100; i++) {
            response.write("\"crap" + i + "\":\"crap\",");
         }
         response.write("\"foo\":\"");
         response.write(fooValue);
         response.write("\"}");
         response.end();
      });
   }

   @Test
   public void testChunkedJsonTransfer() {
      var capturedValue = new AtomicReference<>();
      Locator.push(TestUtil.locator());
      var fooValueReadAccess = SessionFactory.readAccess("foo");
      Locator.pop();
      // @formatter:off
      scenario().initialSequence("test")
            .step(SC).httpRequest(HttpMethod.GET).path("/test4")
            .handler()
            .body(new JsonHandler.Builder().query(".foo")
                  .processors().processor(new StoreProcessor.Builder().toVar("foo").format(DataFormat.STRING)).end())
            .endHandler()
            .sync(true)
            .endStep()
            .step(s -> {
               capturedValue.set(fooValueReadAccess.getObject(s));
               return true;
            })
            .endSequence();
      // @formatter:on
      runScenario();
      assertNotNull(capturedValue.get());
      assertNotNull(fooValue);
      // we're not using equals because the string is too long and will blow the test output on failure!
      assertTrue(fooValue.equals(capturedValue.get()));
   }

   @Test
   public void testChunkedTransfer() {
      AtomicBoolean rawBytesSeen = new AtomicBoolean(false);
      // @formatter:off
      scenario().initialSequence("test")
            .step(s -> {
               HttpDestinationTable.get(s).getConnectionPoolByAuthority(null).connections()
                     .forEach(c -> injectChannelHandler(c, new BufferingDecoder()));
               return true;
            })
            .step(SC).httpRequest(HttpMethod.GET).path("/test")
               .headers().header(HttpHeaderNames.CACHE_CONTROL, "no-cache").endHeaders()
               .handler().rawBytes(new RawBytesHandler() {
                  @Override
                  public void onRequest(Request request, ByteBuf buf, int offset, int length) {
                  }

                  @Override
                  public void onResponse(Request request, ByteBuf byteBuf, int offset, int length, boolean isLastPart) {
                     log.info("Received chunk {} bytes:\n{}", length,
                           byteBuf.toString(offset, length, StandardCharsets.UTF_8));
                     if (byteBuf.toString(StandardCharsets.UTF_8).contains(SHIBBOLETH)) {
                        throw new IllegalStateException();
                     }
                     rawBytesSeen.set(true);
                  }
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

   @Test
   public void testRandomCutBuffers() {
      BaseSequenceBuilder<?> sequence = scenario(64).initialSequence("test")
            .step(s -> {
               HttpDestinationTable.get(s).getConnectionPoolByAuthority(null).connections()
                     .forEach(c -> injectChannelHandler(c, new RandomLengthDecoder()));
               return true;
            });
      AtomicInteger counter = new AtomicInteger();
      for (int i = 0; i < 16; ++i) {
         sequence.step(SC).httpRequest(HttpMethod.GET).path("/test3")
               .headers().header("cache-control", "no-cache").endHeaders()
               .sync(false)
               .handler()
               .body(fragmented -> (session, data, offset, length, isLastPart) -> {
                  String str = Util.toString(data, offset, length);
                  if (str.contains("\n")) {
                     session.fail(new AssertionError(str));
                  }
               })
               .onCompletion(s -> counter.incrementAndGet());
      }
      sequence.step(SC).awaitAllResponses();
      runScenario();
      assertThat(counter.get()).isEqualTo(16 * 64);
   }

   private static void injectChannelHandler(HttpConnection c, ChannelHandler channelHandler) {
      try {
         Field f = c.getClass().getDeclaredField("ctx");
         f.setAccessible(true);
         ChannelHandlerContext ctx = (ChannelHandlerContext) f.get(c);
         if (ctx.pipeline().first().getClass() != channelHandler.getClass()) {
            // Do not inject multiple times
            ctx.pipeline().addFirst(channelHandler);
         }
      } catch (NoSuchFieldException | IllegalAccessException e) {
         throw new IllegalStateException();
      }
   }

   private static class BufferingDecoder extends ChannelInboundHandlerAdapter {
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

      @Override
      public void handlerRemoved(ChannelHandlerContext ctx) {
         if (composite != null && composite.refCnt() > 0) {
            composite.release();
         }
      }
   }

   private static class RandomLengthDecoder extends ChannelInboundHandlerAdapter {
      @Override
      public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
         if (msg instanceof ByteBuf) {
            ByteBuf buf = (ByteBuf) msg;
            ThreadLocalRandom rand = ThreadLocalRandom.current();
            int curr = 0;
            if (buf.readableBytes() == 0) {
               ctx.fireChannelRead(buf);
               return;
            }
            while (curr + buf.readerIndex() < buf.writerIndex()) {
               int len = rand.nextInt(buf.readableBytes() + 1);
               ByteBuf slice = buf.retainedSlice(buf.readerIndex() + curr,
                     Math.min(buf.writerIndex(), buf.readerIndex() + curr + len) - curr - buf.readerIndex());
               ctx.fireChannelRead(slice);
               curr += len;
            }
            buf.release();
         } else {
            super.channelRead(ctx, msg);
         }
      }
   }
}
