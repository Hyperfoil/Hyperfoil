package io.hyperfoil.api.connection;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import io.netty.buffer.ByteBuf;
import io.hyperfoil.api.http.HttpMethod;
import io.hyperfoil.api.session.Session;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface HttpConnection extends Connection {

    void request(Request request, HttpMethod method, String path,
                 BiConsumer<Session, HttpRequestWriter>[] headerAppenders, BiFunction<Session, Connection, ByteBuf> bodyGenerator);

    Request peekRequest(int streamId);

    void setClosed();

    boolean isClosed();
}
