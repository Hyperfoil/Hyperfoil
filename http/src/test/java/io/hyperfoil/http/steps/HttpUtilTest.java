package io.hyperfoil.http.steps;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.hyperfoil.http.HttpUtil;

public class HttpUtilTest {
   @Test
   public void testAuthorityMatch() {
      assertThat(HttpUtil.authorityMatch("http://example.com", "example.com", true)).isTrue();
      assertThat(HttpUtil.authorityMatch("http://example.com/foobar", "example.com:80", true)).isTrue();
      assertThat(HttpUtil.authorityMatch("http://example.com:80", "example.com", true)).isTrue();
      assertThat(HttpUtil.authorityMatch("http://example.com:1234/foobar", "example.com:1234", true)).isTrue();
      assertThat(HttpUtil.authorityMatch("http://example.com:1234/foobar", "example.com", true)).isFalse();
      assertThat(HttpUtil.authorityMatch("http://example.com:1234/foobar", "example.com:8080", true)).isFalse();
      assertThat(HttpUtil.authorityMatch("http://hyperfoil.io/foobar", "example.com", true)).isFalse();
      assertThat(HttpUtil.authorityMatch("http://hyperfoil.io:80", "example.com:80", true)).isFalse();
      assertThat(HttpUtil.authorityMatch("http://hyperfoil.io:1234", "example.com:1234", true)).isFalse();

      assertThat(HttpUtil.authorityMatch("https://example.com", "example.com", false)).isTrue();
      assertThat(HttpUtil.authorityMatch("https://example.com/foobar", "example.com:443", false)).isTrue();
      assertThat(HttpUtil.authorityMatch("https://example.com:443", "example.com", false)).isTrue();
      assertThat(HttpUtil.authorityMatch("https://example.com:1234/foobar", "example.com:1234", false)).isTrue();
      assertThat(HttpUtil.authorityMatch("https://example.com:1234/foobar", "example.com", false)).isFalse();
      assertThat(HttpUtil.authorityMatch("https://example.com:1234/foobar", "example.com:8443", false)).isFalse();
      assertThat(HttpUtil.authorityMatch("https://hyperfoil.io/foobar", "example.com", false)).isFalse();
      assertThat(HttpUtil.authorityMatch("https://hyperfoil.io:443", "example.com:443", false)).isFalse();
      assertThat(HttpUtil.authorityMatch("https://hyperfoil.io:1234", "example.com:1234", false)).isFalse();
   }
}
