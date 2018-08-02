package io.sailrocket.core.client;

import io.sailrocket.api.HttpClient;
import io.vertx.core.http.HttpVersion;

public interface HttpClientPool {

  HttpClientPool threads(int count);
  HttpClientPool ssl(boolean ssl);
  HttpClientPool protocol(HttpVersion protocol);
  HttpClientPool size(int size);
  HttpClientPool port(int port);
  HttpClientPool host(String host);
  HttpClientPool concurrency(int maxConcurrency);
  HttpClient build() throws Exception;

  void shutdown();

}
