package io.sailrocket.core.client.netty;


import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import io.sailrocket.api.HttpMethod;
import io.sailrocket.api.HttpRequest;
import io.sailrocket.core.client.AbstractHttpRequest;

class Http1xRequest extends AbstractHttpRequest {

    private final Http1xConnection connection;
    private final DefaultFullHttpRequest msg;

    Http1xRequest(Http1xConnection connection, HttpMethod method, String path, ByteBuf buf) {
        this.connection = connection;
        this.msg = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method.netty, path, buf == null ? Unpooled.EMPTY_BUFFER : buf, false);
    }

    @Override
    public HttpRequest putHeader(String name, String value) {
      msg.headers().add(name, value);
      return this;
    }

    @Override
    public void end() {
      msg.headers().add("Host", connection.client.host + ":" + connection.client.port);
      connection.ctx.executor().execute(connection.createStream(msg, statusHandler, resetHandler, dataHandler, endHandler));
    }
}
