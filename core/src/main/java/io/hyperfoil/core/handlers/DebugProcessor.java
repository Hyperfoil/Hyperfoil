package io.hyperfoil.core.handlers;

import java.nio.charset.StandardCharsets;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.hyperfoil.api.processor.Processor;
import io.hyperfoil.api.session.Session;
import io.netty.buffer.ByteBuf;

public class DebugProcessor implements Processor {
   private static final Logger log = LogManager.getLogger(DebugProcessor.class);

   @Override
   public void before(Session session) {
      log.debug("Before");
   }

   @Override
   public void process(Session session, ByteBuf data, int offset, int length, boolean isLastPart) {
      log.debug("Process (last? {}): '{}'", isLastPart, data.toString(offset, length, StandardCharsets.UTF_8));
   }

   @Override
   public void after(Session session) {
      log.debug("After");
   }
}
