package io.sailrocket.core.client.netty;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpVersion;
import io.sailrocket.api.connection.Connection;
import io.sailrocket.api.http.HttpMethod;
import io.sailrocket.api.http.HttpRequest;
import io.sailrocket.core.client.AbstractHttpRequest;

class Http1xRequest extends AbstractHttpRequest {

    private final Http1xConnection connection;
    private final DefaultFullHttpRequest msg;

    Http1xRequest(Http1xConnection connection, HttpMethod method, String path, ByteBuf buf) {
        super(method);
        this.connection = connection;
        this.msg = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, method.netty, path, buf == null ? Unpooled.EMPTY_BUFFER : buf, false);
    }

    @Override
    public HttpRequest putHeader(CharSequence name, CharSequence value) {
      msg.headers().add(name, value);
      return this;
    }

    @Override
    public void end() {
      connection.ctx.executor().execute(connection.createStream(msg, this));
    }

   @Override
   public Connection connection() {
      return connection;
   }
}
