package io.hyperfoil.api.http;

import io.hyperfoil.api.config.ServiceLoadedBuilder;
import io.hyperfoil.api.session.Session;
import io.netty.buffer.ByteBuf;

public interface Processor {
   /**
    * Invoked before we record first value from given response.
    * @param session
    */
   default void before(Session session) {
   }

   void process(Session session, ByteBuf data, int offset, int length, boolean isLastPart);

   /**
    * Invoked after we record the last value from given response.
    * @param session
    */
   default void after(Session session) {
   }

   interface BuilderFactory extends ServiceLoadedBuilder.Factory<Processor> {}
}
