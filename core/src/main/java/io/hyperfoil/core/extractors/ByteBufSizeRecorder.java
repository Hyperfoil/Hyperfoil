package io.hyperfoil.core.extractors;

import io.netty.buffer.ByteBuf;
import io.hyperfoil.api.connection.Request;
import io.hyperfoil.api.http.RawBytesHandler;
import io.hyperfoil.api.statistics.LongValue;
import io.hyperfoil.api.statistics.Statistics;

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
