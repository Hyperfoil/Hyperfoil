package io.hyperfoil.api.connection;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import io.netty.buffer.ByteBuf;
import io.hyperfoil.api.session.Session;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface HttpConnection extends Connection {

    void request(HttpRequest request,
                 BiConsumer<Session, HttpRequestWriter>[] headerAppenders,
                 BiFunction<Session, Connection, ByteBuf> bodyGenerator);

    HttpRequest peekRequest(int streamId);

    void setClosed();

    boolean isClosed();

    boolean isSecure();
}
