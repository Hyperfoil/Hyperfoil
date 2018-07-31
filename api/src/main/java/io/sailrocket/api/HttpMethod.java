package io.sailrocket.api;

public enum HttpMethod {

  GET, POST;

  public final io.netty.handler.codec.http.HttpMethod netty;
  public final io.vertx.core.http.HttpMethod vertx;

  HttpMethod() {
    this.netty = io.netty.handler.codec.http.HttpMethod.valueOf(name());
    this.vertx = io.vertx.core.http.HttpMethod.valueOf(name());
  }
}
