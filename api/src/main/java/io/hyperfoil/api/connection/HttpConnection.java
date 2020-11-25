package io.hyperfoil.api.connection;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import io.hyperfoil.api.http.HttpVersion;
import io.netty.buffer.ByteBuf;
import io.hyperfoil.api.session.Session;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface HttpConnection extends Connection {

   void attach(HttpConnectionPool pool);

   void request(HttpRequest request,
                BiConsumer<Session, HttpRequestWriter>[] headerAppenders,
                boolean injectHostHeader,
                BiFunction<Session, Connection, ByteBuf> bodyGenerator);

   HttpRequest dispatchedRequest();

   HttpRequest peekRequest(int streamId);

   void removeRequest(int streamId, HttpRequest request);

   boolean isSecure();

   HttpVersion version();

   enum Status {
      OPEN,
      CLOSING,
      CLOSED,
   }
}
