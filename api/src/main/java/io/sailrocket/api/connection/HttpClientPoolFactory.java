package io.sailrocket.api.connection;

import java.io.Serializable;

import io.vertx.core.http.HttpVersion;

public interface HttpClientPoolFactory extends Serializable {

  HttpClientPoolFactory threads(int count);
  HttpClientPoolFactory ssl(boolean ssl);
  HttpClientPoolFactory protocol(HttpVersion protocol);
  HttpClientPoolFactory size(int size);
  HttpClientPoolFactory port(int port);
  HttpClientPoolFactory host(String host);
  HttpClientPoolFactory concurrency(int maxConcurrency);
  HttpClientPool build() throws Exception;
}
