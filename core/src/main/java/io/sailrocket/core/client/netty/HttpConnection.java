package io.sailrocket.core.client.netty;

import io.netty.buffer.ByteBuf;
import io.sailrocket.api.connection.Connection;
import io.sailrocket.api.http.HttpMethod;
import io.sailrocket.api.http.HttpRequest;
import io.sailrocket.api.http.HttpResponseHandlers;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface HttpConnection extends Connection {

    HttpRequest request(HttpMethod method, String path, ByteBuf body);

    HttpResponseHandlers currentResponseHandlers(int streamId);
}
