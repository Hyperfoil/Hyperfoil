package io.hyperfoil.http.steps;

import java.util.Arrays;

import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.session.SequenceInstance;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.statistics.Statistics;
import io.hyperfoil.core.metric.MetricSelector;
import io.hyperfoil.core.steps.StatisticsStep;
import io.hyperfoil.function.SerializableFunction;
import io.hyperfoil.http.HttpRequestPool;
import io.hyperfoil.http.HttpUtil;
import io.hyperfoil.http.api.HttpConnectionPool;
import io.hyperfoil.http.api.HttpDestinationTable;
import io.hyperfoil.http.api.HttpMethod;
import io.hyperfoil.http.api.HttpRequest;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class PrepareHttpRequestStep extends StatisticsStep implements ResourceUtilizer {
   private static final Logger log = LogManager.getLogger(PrepareHttpRequestStep.class);

   final HttpRequestContext.Key contextKey;
   final SerializableFunction<Session, HttpMethod> method;
   final SerializableFunction<Session, String> endpoint;
   final SerializableFunction<Session, String> authority;
   final SerializableFunction<Session, String> pathGenerator;
   final MetricSelector metricSelector;
   final HttpResponseHandlersImpl handler;

   public PrepareHttpRequestStep(int stepId, HttpRequestContext.Key contextKey,
                                 SerializableFunction<Session, HttpMethod> method,
                                 SerializableFunction<Session, String> endpoint,
                                 SerializableFunction<Session, String> authority,
                                 SerializableFunction<Session, String> pathGenerator,
                                 MetricSelector metricSelector,
                                 HttpResponseHandlersImpl handler) {
      super(stepId);
      this.contextKey = contextKey;
      this.method = method;
      this.endpoint = endpoint;
      this.authority = authority;
      this.pathGenerator = pathGenerator;
      this.metricSelector = metricSelector;
      this.handler = handler;
   }

   @Override
   public boolean invoke(Session session) {
      SequenceInstance sequence = session.currentSequence();
      HttpRequestContext context = session.getResource(contextKey);
      if (context.request == null) {
         context.request = HttpRequestPool.get(session).acquire();
         if (context.request == null) {
            log.warn("#{} Request pool too small; increase it to prevent blocking.", session.uniqueId());
            return false;
         }
      }
      HttpDestinationTable destinations = HttpDestinationTable.get(session);
      try {
         HttpRequest request = context.request;
         request.method = method.apply(session);
         String path = pathGenerator.apply(session);
         boolean isHttp = path.startsWith(HttpUtil.HTTP_PREFIX);
         boolean isUrl = isHttp || path.startsWith(HttpUtil.HTTPS_PREFIX);
         if (isUrl) {
            int pathIndex = path.indexOf('/', HttpUtil.prefixLength(isHttp));
            if (pathIndex < 0) {
               request.path = "/";
            } else {
               request.path = path.substring(pathIndex);
            }
         } else {
            request.path = path;
         }

         HttpConnectionPool connectionPool = getConnectionPool(session, destinations, path, isHttp, isUrl);
         if (connectionPool == null) {
            session.fail(new IllegalStateException("Connection pool must not be null."));
            return false;
         }
         request.authority = connectionPool.clientPool().authority();
         String metric = destinations.hasSingleDestination() ?
               metricSelector.apply(null, request.path) : metricSelector.apply(request.authority, request.path);
         Statistics statistics = session.statistics(id(), metric);
         request.start(connectionPool, handler, session.currentSequence(), statistics);
         connectionPool.acquire(false, context);
      } catch (Throwable t) {
         // If any error happens we still need to release the request
         // The request is either IDLE or RUNNING - we need to make it running, otherwise we could not release it
         if (!context.request.isRunning()) {
            context.request.start(sequence, null);
         }
         context.request.setCompleted();
         context.request.release();
         context.reset();
         throw t;
      }
      return true;
   }

   private HttpConnectionPool getConnectionPool(Session session, HttpDestinationTable destinations, String path, boolean isHttp, boolean isUrl) {
      String endpoint = this.endpoint == null ? null : this.endpoint.apply(session);
      if (endpoint != null) {
         HttpConnectionPool connectionPool = destinations.getConnectionPoolByName(endpoint);
         if (connectionPool == null) {
            throw new IllegalStateException("There is no HTTP destination '" + endpoint + "'");
         }
         return connectionPool;
      }
      String authority = this.authority == null ? null : this.authority.apply(session);
      if (authority == null && isUrl) {
         for (String hostPort : destinations.authorities()) {
            if (HttpUtil.authorityMatch(path, hostPort, isHttp)) {
               authority = hostPort;
               break;
            }
         }
         if (authority == null) {
            throw new IllegalStateException("Cannot access " + path + ": no destination configured");
         }
      }
      HttpConnectionPool connectionPool = destinations.getConnectionPoolByAuthority(authority);
      if (connectionPool == null) {
         if (authority == null) {
            throw new IllegalStateException("There is no default authority and it was not set neither explicitly nor through URL in path.");
         } else {
            throw new IllegalStateException("There is no connection pool with authority '" + authority +
                  "', available pools are: " + Arrays.asList(destinations.authorities()));
         }
      }
      return connectionPool;
   }

   @Override
   public void reserve(Session session) {
      session.declareResources().add(contextKey, HttpRequestContext::new);
   }
}
