package io.hyperfoil.http.steps;

import java.util.concurrent.TimeUnit;

import io.hyperfoil.api.config.Visitor;
import io.hyperfoil.api.connection.Connection;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.builders.SLA;
import io.hyperfoil.core.steps.StatisticsStep;
import io.hyperfoil.function.SerializableBiConsumer;
import io.hyperfoil.function.SerializableBiFunction;
import io.hyperfoil.http.api.HttpRequest;
import io.hyperfoil.http.api.HttpRequestWriter;
import io.netty.buffer.ByteBuf;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class SendHttpRequestStep extends StatisticsStep implements ResourceUtilizer, SLA.Provider {
   private static final Logger log = LogManager.getLogger(SendHttpRequestStep.class);
   private static final boolean trace = log.isTraceEnabled();

   final HttpRequestContext.Key contextKey;
   final SerializableBiFunction<Session, Connection, ByteBuf> bodyGenerator;
   final SerializableBiConsumer<Session, HttpRequestWriter>[] headerAppenders;
   @Visitor.Ignore
   private final boolean injectHostHeader;
   final long timeout;
   final SLA[] sla;

   public SendHttpRequestStep(int stepId, HttpRequestContext.Key contextKey,
                              SerializableBiFunction<Session, Connection, ByteBuf> bodyGenerator,
                              SerializableBiConsumer<Session, HttpRequestWriter>[] headerAppenders,
                              boolean injectHostHeader,
                              long timeout, SLA[] sla) {
      super(stepId);
      this.contextKey = contextKey;
      this.bodyGenerator = bodyGenerator;
      this.headerAppenders = headerAppenders;
      this.injectHostHeader = injectHostHeader;
      this.timeout = timeout;
      this.sla = sla;
   }

   @Override
   public boolean invoke(Session session) {
      HttpRequestContext context = session.getResource(contextKey);
      if (!context.ready) {
         // TODO: when the phase is finished, max duration is not set and the connection cannot be obtained
         // we'll be waiting here forever. Maybe there should be a (default) timeout to obtain the connection.
         context.startWaiting();
         return false;
      }
      if (context.connection == null) {
         log.error("#{} Stopping the session as we cannot obtain connection.", session.uniqueId());
         session.stop();
         return false;
      }
      context.stopWaiting();

      HttpRequest request = context.request;
      request.send(context.connection, headerAppenders, injectHostHeader, bodyGenerator);
      // We don't need the context anymore and we need to reset it (in case the step is repeated).
      context.reset();
      request.statistics().incrementRequests(request.startTimestampMillis());

      if (request.isCompleted()) {
         // When the request handlers call Session.stop() due to a failure it does not make sense to continue
         request.release();
         return true;
      }
      // Set up timeout only after successful request
      if (timeout > 0) {
         // TODO alloc!
         request.setTimeout(timeout, TimeUnit.MILLISECONDS);
      } else {
         long timeout = request.connection().config().requestTimeout();
         if (timeout > 0) {
            request.setTimeout(timeout, TimeUnit.MILLISECONDS);
         }
      }

      if (trace) {
         log.trace("#{} sent to {} request on {}", session.uniqueId(), request.path, request.connection());
      }
      return true;
   }

   @Override
   public void reserve(Session session) {
      ResourceUtilizer.reserve(session, bodyGenerator);
      ResourceUtilizer.reserve(session, (Object[]) headerAppenders);
   }

   @Override
   public SLA[] sla() {
      return sla;
   }

}
