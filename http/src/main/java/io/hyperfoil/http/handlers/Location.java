package io.hyperfoil.http.handlers;

import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.Action;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.data.LimitedPoolResource;
import io.hyperfoil.core.data.Queue;
import io.hyperfoil.core.session.ObjectVar;
import io.hyperfoil.function.SerializableFunction;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class Location {
   private static final Logger log = LogManager.getLogger(Location.class);
   private static final boolean trace = log.isTraceEnabled();

   public CharSequence authority;
   public CharSequence path;

   public Location reset() {
      authority = null;
      path = null;
      return this;
   }

   public static class GetAuthority implements SerializableFunction<Session, String> {
      private final Access locationVar;

      public GetAuthority(Access locationVar) {
         this.locationVar = locationVar;
      }

      @Override
      public String apply(Session session) {
         Location location = (Location) locationVar.getObject(session);
         return location.authority == null ? null : location.authority.toString();
      }
   }

   public static class GetPath implements SerializableFunction<Session, String> {
      private final Access locationVar;

      public GetPath(Access locationVar) {
         this.locationVar = locationVar;
      }

      @Override
      public String apply(Session session) {
         Location location = (Location) locationVar.getObject(session);
         return location.path.toString();
      }
   }

   public static class Complete<T extends Location> implements Action {
      private final LimitedPoolResource.Key<T> poolKey;
      private final Session.ResourceKey<Queue> queueKey;
      private final Access locationVar;

      public Complete(LimitedPoolResource.Key<T> poolKey, Queue.Key queueKey, Access locationVar) {
         this.poolKey = poolKey;
         this.queueKey = queueKey;
         this.locationVar = locationVar;
      }

      @Override
      public void run(Session session) {
         LimitedPoolResource<T> pool = session.getResource(poolKey);
         ObjectVar var = (ObjectVar) locationVar.getVar(session);
         Location location = (Location) var.get();
         if (trace) {
            log.trace("#{} releasing {} from {}[{}]", session.uniqueId(), location, locationVar, session.currentSequence().index());
         }
         @SuppressWarnings("unchecked")
         T castLocation = (T) location.reset();
         pool.release(castLocation);
         var.set(null);
         var.unset();
         session.getResource(queueKey).consumed(session);
      }
   }
}
