package io.hyperfoil.core.handlers;

import java.nio.charset.StandardCharsets;

import io.hyperfoil.api.connection.Request;
import io.hyperfoil.api.connection.Processor;
import io.netty.buffer.ByteBuf;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class DebugProcessor implements Processor<Request> {
   private static final Logger log = LoggerFactory.getLogger(DebugProcessor.class);

   @Override
   public void before(Request request) {
      log.debug("Before");
   }

   @Override
   public void process(Request request, ByteBuf data, int offset, int length, boolean isLastPart) {
      log.debug("Process (last? {}): '{}'", isLastPart, data.toString(offset, length, StandardCharsets.UTF_8));
   }

   @Override
   public void after(Request request) {
      log.debug("After");
   }
}
