package io.sailrocket.core.extractors;

import io.netty.buffer.ByteBuf;
import io.sailrocket.api.session.Session;
import io.sailrocket.api.statistics.LongValue;
import io.sailrocket.api.statistics.Statistics;
import io.sailrocket.function.SerializableBiConsumer;

public class ByteBufSizeRecorder implements SerializableBiConsumer<Session, ByteBuf> {
   private final String name;

   public ByteBufSizeRecorder(String name) {
      this.name = name;
   }

   @Override
   public void accept(Session session, ByteBuf buf) {
      Statistics statistics = session.requestQueue().peek().sequence.statistics(session);
      statistics.getCustom(name, LongValue::new).add(buf.readableBytes());
   }
}
