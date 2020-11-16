package io.hyperfoil.core.handlers.html;

import static io.hyperfoil.api.connection.HttpRequest.log;

import java.nio.charset.StandardCharsets;

import io.hyperfoil.api.connection.HttpRequest;
import io.hyperfoil.api.http.HttpMethod;
import io.hyperfoil.api.processor.Processor;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.session.SequenceInstance;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.data.LimitedPoolResource;
import io.hyperfoil.core.data.Queue;
import io.hyperfoil.core.handlers.http.Redirect;
import io.hyperfoil.core.http.HttpUtil;
import io.hyperfoil.core.session.ObjectVar;
import io.hyperfoil.core.util.Util;
import io.hyperfoil.function.SerializableFunction;
import io.netty.buffer.ByteBuf;

public class RefreshHandler implements Processor, ResourceUtilizer {
   private static final byte[] URL = "url".getBytes(StandardCharsets.UTF_8);
   private final Queue.Key immediateQueueKey;
   private final Queue.Key delayedQueueKey;
   private final LimitedPoolResource.Key<Redirect.Coords> poolKey;
   private final int concurrency;
   private final Access immediateQueueVar;
   private final Access delayedQueueVar;
   private final String redirectSequence;
   private final String delaySequence;
   private final Access tempCoordsVar;
   private final SerializableFunction<Session, SequenceInstance> originalSequenceSupplier;

   public RefreshHandler(Queue.Key immediateQueueKey, Queue.Key delayedQueueKey, LimitedPoolResource.Key<Redirect.Coords> poolKey, int concurrency, Access immediateQueueVar, Access delayedQueueVar, String redirectSequence, String delaySequence, Access tempCoordsVar, SerializableFunction<Session, SequenceInstance> originalSequenceSupplier) {
      this.immediateQueueKey = immediateQueueKey;
      this.delayedQueueKey = delayedQueueKey;
      this.poolKey = poolKey;
      this.concurrency = concurrency;
      this.immediateQueueVar = immediateQueueVar;
      this.delayedQueueVar = delayedQueueVar;
      this.redirectSequence = redirectSequence;
      this.delaySequence = delaySequence;
      this.tempCoordsVar = tempCoordsVar;
      this.originalSequenceSupplier = originalSequenceSupplier;
   }

   @Override
   public void before(Session session) {
      tempCoordsVar.unset(session);
   }

   @Override
   public void process(Session session, ByteBuf data, int offset, int length, boolean isLastPart) {
      assert isLastPart;

      try {
         long seconds = 0;
         String url = null;
         for (int i = 0; i < length; ++i) {
            if (data.getByte(offset + i) == ';') {
               seconds = Util.parseLong(data, offset, i);
               ++i;
               while (Character.isWhitespace(data.getByte(offset + i))) ++i;
               while (length > 0 && Character.isWhitespace(data.getByte(offset + length - 1))) --length;
               for (int j = 0; j < URL.length; ++j, ++i) {
                  if (Util.toLowerCase(data.getByte(offset + i)) != URL[j]) {
                     log.warn("#{} Failed to parse META refresh content (missing URL): {}", session.uniqueId(), Util.toString(data, offset, length));
                     return;
                  }
               }
               while (Character.isWhitespace(data.getByte(offset + i))) ++i;
               if (data.getByte(offset + i) != '=') {
                  log.warn("#{} Failed to parse META refresh content (missing = after URL): {}", session.uniqueId(), Util.toString(data, offset, length));
                  return;
               } else {
                  ++i;
               }
               while (Character.isWhitespace(data.getByte(offset + i))) ++i;
               url = Util.toString(data, offset + i, length - i);
               break;
            }
         }
         if (url == null) {
            seconds = Util.parseLong(data, offset, length);
         }

         Redirect.Coords coords = session.getResource(poolKey).acquire();
         coords.method = HttpMethod.GET;
         coords.originalSequence = originalSequenceSupplier.apply(session);
         coords.delay = (int) seconds;
         if (url == null) {
            HttpRequest request = (HttpRequest) session.currentRequest();
            coords.authority = request.authority;
            coords.path = request.path;
         } else if (url.startsWith(HttpUtil.HTTP_PREFIX) || url.startsWith(HttpUtil.HTTPS_PREFIX)) {
            coords.authority = null;
            coords.path = url;
         } else {
            HttpRequest request = (HttpRequest) session.currentRequest();
            coords.authority = request.authority;
            if (url.startsWith("/")) {
               coords.path = url;
            } else {
               int lastSlash = request.path.lastIndexOf('/');
               if (lastSlash < 0) {
                  log.warn("#{} Did the request have a relative path? {}", session.uniqueId(), request.path);
                  coords.path = "/" + url;
               } else {
                  coords.path = request.path.substring(0, lastSlash + 1) + url;
               }
            }
         }

         session.getResource(seconds == 0 ? immediateQueueKey : delayedQueueKey).push(session, coords);
         // this prevents completion handlers from running
         tempCoordsVar.setObject(session, coords);
      } catch (NumberFormatException e) {
         log.warn("#{} Failed to parse META refresh content: {}", session.uniqueId(), Util.toString(data, offset, length));
      }
   }

   @Override
   public void reserve(Session session) {
      tempCoordsVar.declareObject(session);
      session.declareResource(poolKey, () -> LimitedPoolResource.create(concurrency, Redirect.Coords.class, Redirect.Coords::new), true);
      session.declareResource(immediateQueueKey, () -> new Queue(immediateQueueVar, concurrency, concurrency, redirectSequence, null), true);
      session.declareResource(delayedQueueKey, () -> new Queue(delayedQueueVar, concurrency, concurrency, delaySequence, null), true);
      initQueueVar(session, immediateQueueVar);
      initQueueVar(session, delayedQueueVar);
   }

   private void initQueueVar(Session session, Access var) {
      var.declareObject(session);
      if (!var.isSet(session)) {
         var.setObject(session, ObjectVar.newArray(session, concurrency));
      }
   }


}
