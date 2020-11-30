package io.hyperfoil.core.util;

import io.hyperfoil.api.connection.Connection;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.function.SerializableBiFunction;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class FromVarBytesGenerator implements SerializableBiFunction<Session, Connection, ByteBuf> {
   private static final Logger log = LoggerFactory.getLogger(FromVarBytesGenerator.class);

   private final Access fromVar;

   public FromVarBytesGenerator(Access fromVar) {
      this.fromVar = fromVar;
   }

   @Override
   public ByteBuf apply(Session session, Connection connection) {
      Object value = fromVar.getObject(session);
      if (value instanceof ByteBuf) {
         return (ByteBuf) value;
      } else if (value instanceof String) {
         String str = (String) value;
         return Util.string2byteBuf(str, connection.context().alloc().buffer(str.length()));
      } else if (value instanceof byte[]) {
         return Unpooled.wrappedBuffer((byte[]) value);
      } else {
         log.error("#{} Cannot encode contents of var {}: {}", session.uniqueId(), fromVar, value);
         return null;
      }
   }
}
