package io.hyperfoil.http.handlers;

import org.junit.jupiter.api.Test;

import io.hyperfoil.api.config.Locator;
import io.hyperfoil.api.session.ObjectAccess;
import io.hyperfoil.api.session.WriteAccess;
import io.hyperfoil.core.handlers.ExpectProcessor;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.core.test.TestUtil;
import io.hyperfoil.http.BaseMockConnection;
import io.hyperfoil.http.api.HttpRequest;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;

public class FilterHeaderHandlerTest {
   @Test
   public void testValue() {
      ExpectProcessor expect = new ExpectProcessor().expect(0, 6, true);
      FilterHeaderHandler handler = new FilterHeaderHandler.Builder()
            .processor(f -> expect)
            .header().value("foo").end()
            .build();
      HttpRequest request = requestMock();
      TestUtil.resolveAccess(request.session, handler);
      handler.beforeHeaders(request);
      handler.handleHeader(request, "Foo", "barxxx");
      handler.handleHeader(request, "moo", "xxx");
      handler.afterHeaders(request);
      expect.validate();
   }

   @Test
   public void testStartsWith() {
      ExpectProcessor expect = new ExpectProcessor().expect(0, 6, true);
      FilterHeaderHandler handler = new FilterHeaderHandler.Builder()
            .processor(f -> expect)
            .header().startsWith("foo").end()
            .build();
      HttpRequest request = requestMock();
      TestUtil.resolveAccess(request.session, handler);
      handler.beforeHeaders(request);
      handler.handleHeader(request, "FooBar", "barxxx");
      handler.handleHeader(request, "fo", "xxx");
      handler.handleHeader(request, "moobar", "xxx");
      handler.afterHeaders(request);
      expect.validate();
   }

   @Test
   public void testEndsWith() {
      ExpectProcessor expect = new ExpectProcessor().expect(0, 6, true);
      FilterHeaderHandler handler = new FilterHeaderHandler.Builder()
            .processor(f -> expect)
            .header().endsWith("bar").end()
            .build();
      HttpRequest request = requestMock();
      TestUtil.resolveAccess(request.session, handler);
      handler.beforeHeaders(request);
      handler.handleHeader(request, "FooBar", "barxxx");
      handler.handleHeader(request, "ar", "xxx");
      handler.handleHeader(request, "moomar", "xxx");
      handler.afterHeaders(request);
      expect.validate();
   }

   @Test
   public void testMatchVar() {
      ExpectProcessor expect = new ExpectProcessor().expect(0, 6, true);
      Locator.push(TestUtil.locator());
      FilterHeaderHandler handler = new FilterHeaderHandler.Builder()
            .processor(f -> expect)
            .header()
            .matchVar("myVar")
            .end()
            .build();
      ObjectAccess access = SessionFactory.objectAccess("myVar");
      HttpRequest request = requestMock(access);
      TestUtil.resolveAccess(request.session, handler);
      access.setObject(request.session, "Foo");
      Locator.pop();

      handler.beforeHeaders(request);
      handler.handleHeader(request, "foo", "barxxx");
      handler.handleHeader(request, "moo", "xxx");
      handler.afterHeaders(request);
      expect.validate();
   }

   private HttpRequest requestMock(WriteAccess... accesses) {
      HttpRequest request = new HttpRequest(SessionFactory.forTesting(accesses), true);
      request.attach(new BaseMockConnection() {
         @Override
         public ChannelHandlerContext context() {
            return new EmbeddedChannel().pipeline().addFirst(new ChannelHandlerAdapter() {
            }).firstContext();
         }
      });
      return request;
   }
}
