package io.sailrocket.core.client;

import io.vertx.core.http.HttpVersion;

public interface HttpClientBuilder {

  HttpClientBuilder threads(int count);
  HttpClientBuilder ssl(boolean ssl);
  HttpClientBuilder protocol(HttpVersion protocol);
  HttpClientBuilder size(int size);
  HttpClientBuilder port(int port);
  HttpClientBuilder host(String host);
  HttpClientBuilder concurrency(int maxConcurrency);
  HttpClient build() throws Exception;

  void shutdown();

}
