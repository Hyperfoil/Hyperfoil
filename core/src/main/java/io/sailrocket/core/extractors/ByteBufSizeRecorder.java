package io.sailrocket.core.extractors;

import io.netty.buffer.ByteBuf;
import io.sailrocket.api.connection.Request;
import io.sailrocket.api.http.RawBytesHandler;
import io.sailrocket.api.statistics.LongValue;
import io.sailrocket.api.statistics.Statistics;

public class ByteBufSizeRecorder implements RawBytesHandler {
   private final String name;

   public ByteBufSizeRecorder(String name) {
      this.name = name;
   }

   @Override
   public void accept(Request request, ByteBuf buf) {
      Statistics statistics = request.sequence().statistics(request.session);
      statistics.getCustom(name, LongValue::new).add(buf.readableBytes());
   }
}
