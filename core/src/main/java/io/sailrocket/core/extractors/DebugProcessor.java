package io.sailrocket.core.extractors;

import java.nio.charset.Charset;

import io.netty.buffer.ByteBuf;
import io.sailrocket.api.Session;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class DebugProcessor implements Session.Processor {
   private static final Logger log = LoggerFactory.getLogger(DebugProcessor.class);

   @Override
   public void before(Session session) {
      log.debug("Before");
   }

   @Override
   public void process(Session session, ByteBuf data, int offset, int length) {
      log.debug("Process: '{}'", data.toString(offset, length, Charset.forName("UTF-8")));
   }

   @Override
   public void after(Session session) {
      log.debug("After");
   }
}
