package io.hyperfoil.http.handlers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.connection.Request;
import io.hyperfoil.http.api.HttpRequest;
import io.hyperfoil.api.processor.Processor;
import io.hyperfoil.http.api.HeaderHandler;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.util.Util;
import io.netty.buffer.ByteBuf;

public class LogInvalidHandler implements Processor, HeaderHandler {
   private static final Logger log = LogManager.getLogger(LogInvalidHandler.class);

   @Override
   public void process(Session session, ByteBuf data, int offset, int length, boolean isLast) {
      Request request = session.currentRequest();
      if (request != null && !request.isValid()) {
         if (request instanceof HttpRequest) {
            HttpRequest httpRequest = (HttpRequest) request;
            log.debug("#{}: {} {}/{}, {} bytes: {}", session.uniqueId(), httpRequest.method, httpRequest.authority, httpRequest.path, data.readableBytes(),
                  Util.toString(data, data.readerIndex(), data.readableBytes()));
         } else {
            log.debug("#{}: {} bytes: {}", session.uniqueId(), data.readableBytes(),
                  Util.toString(data, data.readerIndex(), data.readableBytes()));
         }
      }
   }

   @Override
   public void handleHeader(HttpRequest request, CharSequence header, CharSequence value) {
      if (!request.isValid()) {
         log.debug("#{}: {} {}/{}, {}: {}", request.session.uniqueId(), request.method, request.authority, request.path, header, value);
      }
   }

   /**
    * Logs body chunks from requests marked as invalid.
    */
   @MetaInfServices(Processor.Builder.class)
   @Name("logInvalid")
   public static class BodyHandlerBuilder implements Processor.Builder {
      @Override
      public LogInvalidHandler build(boolean fragmented) {
         return new LogInvalidHandler();
      }
   }

   /**
    * Logs headers from requests marked as invalid.
    */
   @MetaInfServices(HeaderHandler.Builder.class)
   @Name("logInvalid")
   public static class HeaderHandlerBuilder implements HeaderHandler.Builder {
      @Override
      public LogInvalidHandler build() {
         return new LogInvalidHandler();
      }
   }
}
