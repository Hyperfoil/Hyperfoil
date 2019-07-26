package io.hyperfoil.core.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;

import org.junit.Test;

import io.hyperfoil.api.connection.HttpConnection;
import io.hyperfoil.api.connection.HttpRequest;
import io.hyperfoil.api.connection.HttpRequestWriter;

public class CookieStoreTest {
   @Test
   public void testSubdomainRequest() {
      CookieStore store = new CookieStore();
      store.setCookie("hyperfoil.io", "/", "foo=bar; domain=hyperfoil.io;");

      MockWriter writer = new MockWriter("foo.hyperfoil.io", "/");
      store.appendCookies(writer);
      assertThat(writer.values.size()).isEqualTo(1);
      assertThat(writer.values.get(0)).isEqualTo("foo=bar");
   }

   @Test
   public void testNoDomainRequest() {
      CookieStore store = new CookieStore();
      store.setCookie("hyperfoil.io", "/", "foo=bar;");

      MockWriter writer1 = new MockWriter("foo.hyperfoil.io", "/");
      store.appendCookies(writer1);
      assertThat(writer1.values.size()).isEqualTo(0);

      MockWriter writer2 = new MockWriter("hyperfoil.io", "/");
      store.appendCookies(writer2);
      assertThat(writer2.values.size()).isEqualTo(1);
      assertThat(writer2.values.get(0)).isEqualTo("foo=bar");
   }

   @Test
   public void testSetSuperdomain() {
      CookieStore store1 = new CookieStore();
      store1.setCookie("foo.hyperfoil.io", "/", "foo=bar; domain=hyperfoil.io;");

      MockWriter writer = new MockWriter("foo.hyperfoil.io", "/");
      store1.appendCookies(writer);
      assertThat(writer.values.size()).isEqualTo(1);
      assertThat(writer.values.get(0)).isEqualTo("foo=bar");

      CookieStore store2 = new CookieStore();
      store2.setCookie("foo.hyperfoil.io", "/", "foo=bar; domain=bar.io;");

      MockWriter writer2 = new MockWriter("foo.hyperfoil.io", "/");
      store2.appendCookies(writer2);
      assertThat(writer2.values.size()).isEqualTo(0);

      MockWriter writer3 = new MockWriter("bar.io", "/");
      store2.appendCookies(writer3);
      assertThat(writer3.values.size()).isEqualTo(0);
   }

   @Test
   public void testSubpathRequest() {
      CookieStore store = new CookieStore();
      store.setCookie("hyperfoil.io", "/foo/", "foo=bar; path=/foo");

      MockWriter writer1 = new MockWriter("hyperfoil.io", "/foo/bar.php");
      store.appendCookies(writer1);
      assertThat(writer1.values.size()).isEqualTo(1);
      assertThat(writer1.values.get(0)).isEqualTo("foo=bar");

      MockWriter writer2 = new MockWriter("foo.hyperfoil.io", "/");
      store.appendCookies(writer2);
      assertThat(writer2.values.size()).isEqualTo(0);
   }

   @Test
   public void testRootPath() {
      CookieStore store = new CookieStore();
      store.setCookie("hyperfoil.io", "/foo.php", "foo=bar; path=/");

      MockWriter writer1 = new MockWriter("hyperfoil.io", "/foo/bar.php");
      store.appendCookies(writer1);
      assertThat(writer1.values.size()).isEqualTo(1);
      assertThat(writer1.values.get(0)).isEqualTo("foo=bar");
   }

   @Test
   public void testSetSuperpath() {
      CookieStore store1 = new CookieStore();
      store1.setCookie("hyperfoil.io", "/foo/bar.php", "foo=bar; path=/");

      MockWriter writer = new MockWriter("hyperfoil.io", "/xxx/yyy");
      store1.appendCookies(writer);
      assertThat(writer.values.size()).isEqualTo(1);
      assertThat(writer.values.get(0)).isEqualTo("foo=bar");

      CookieStore store2 = new CookieStore();
      store2.setCookie("hyperfoil.io", "/foo/bar.php", "foo=bar; path=/bar");

      MockWriter writer2 = new MockWriter("hyperfoil.io", "/bar/goo");
      store2.appendCookies(writer2);
      assertThat(writer2.values.size()).isEqualTo(0);

      MockWriter writer3 = new MockWriter("hyperfoil.io", "/xxx");
      store2.appendCookies(writer3);
      assertThat(writer3.values.size()).isEqualTo(0);
   }

   @Test
   public void testExpiration() {
      CookieStore store = new CookieStore();
      store.setCookie("hyperfoil.io", "/", "foo=bar; expires=Mon, 12-Jul-2038 14:52:12 GMT");
      {
         MockWriter writer = new MockWriter("hyperfoil.io", "/");
         store.appendCookies(writer);
         assertThat(writer.values.size()).isEqualTo(1);
         assertThat(writer.values.get(0)).isEqualTo("foo=bar");
      }
      store.setCookie("hyperfoil.io", "/", "foo=bar; expires=Mon, 15-Sep-2012 16:11:45 GMT");
      {
         MockWriter writer = new MockWriter("foo.hyperfoil.io", "/");
         store.appendCookies(writer);
         assertThat(writer.values.size()).isEqualTo(0);
      }
      store.setCookie("hyperfoil.io", "/", "foo=bar; expires=Mon, 22-Jul-2038 14:52:12 GMT");
      {
         MockWriter writer = new MockWriter("hyperfoil.io", "/");
         store.appendCookies(writer);
         assertThat(writer.values.size()).isEqualTo(1);
         assertThat(writer.values.get(0)).isEqualTo("foo=bar");
      }
      store.setCookie("hyperfoil.io", "/", "foo=bar; Max-Age=-1");
      {
         MockWriter writer = new MockWriter("foo.hyperfoil.io", "/");
         store.appendCookies(writer);
         assertThat(writer.values.size()).isEqualTo(0);
      }
      store.setCookie("hyperfoil.io", "/", "foo=bar; max-age=86400");
      {
         MockWriter writer = new MockWriter("hyperfoil.io", "/");
         store.appendCookies(writer);
         assertThat(writer.values.size()).isEqualTo(1);
         assertThat(writer.values.get(0)).isEqualTo("foo=bar");
      }
   }

   private static class MockWriter implements HttpRequestWriter {
      final String host;
      final String path;
      final ArrayList<CharSequence> values = new ArrayList<>();

      private MockWriter(String host, String path) {
         this.host = host;
         this.path = path;
      }

      @Override
      public HttpConnection connection() {
         return new BaseMockConnection() {
            @Override
            public String host() {
               return host;
            }
         };
      }

      @Override
      public HttpRequest request() {
         HttpRequest httpRequest = new HttpRequest(null);
         httpRequest.path = path;
         return httpRequest;
      }

      @Override
      public void putHeader(CharSequence header, CharSequence value) {
         values.add(value);
      }
   }

}
