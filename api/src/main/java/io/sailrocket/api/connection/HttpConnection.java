package io.sailrocket.api.connection;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import io.netty.buffer.ByteBuf;
import io.sailrocket.api.http.HttpMethod;
import io.sailrocket.api.session.Session;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface HttpConnection extends Connection {

    void request(Request request, HttpMethod method, Function<Session, String> pathGenerator,
                 BiConsumer<Session, HttpRequestWriter>[] headerAppenders, BiFunction<Session, Connection, ByteBuf> bodyGenerator);

    Request peekRequest(int streamId);
}
