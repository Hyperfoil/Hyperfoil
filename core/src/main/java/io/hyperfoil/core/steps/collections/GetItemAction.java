package io.hyperfoil.core.steps.collections;

import java.lang.reflect.Array;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.session.Action;
import io.hyperfoil.api.session.ObjectAccess;
import io.hyperfoil.api.session.ReadAccess;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.builders.IntSourceBuilder;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.function.SerializableToIntFunction;

public class GetItemAction implements Action {
   private static final Logger log = LogManager.getLogger(GetItemAction.class);

   private final ReadAccess fromVar;
   private final SerializableToIntFunction<Session> index;
   private final ObjectAccess toVar;

   public GetItemAction(ReadAccess fromVar, SerializableToIntFunction<Session> index, ObjectAccess toVar) {
      this.fromVar = fromVar;
      this.index = index;
      this.toVar = toVar;
   }

   @Override
   public void run(Session session) {
      if (!fromVar.isSet(session)) {
         log.error("#{} Variable {} is not set", session.uniqueId(), fromVar);
         return;
      }
      Object obj = fromVar.getObject(session);
      int index = this.index.applyAsInt(session);
      if (obj == null) {
         log.error("#{} Variable {} is null", session.uniqueId(), fromVar);
      } else if (obj.getClass().isArray()) {
         int length = Array.getLength(obj);
         if (length <= index) {
            log.error("#{} Index {} out of bounds: array in {} has {} elements", session.uniqueId(), index, fromVar, length);
            return;
         }
         Object item = Array.get(obj, index);
         if (item instanceof Session.Var) {
            Session.Var var = (Session.Var) item;
            if (var.isSet()) {
               item = var.objectValue(session);
            } else {
               log.error("#{} Cannot access {}[{}]: not set.", session.uniqueId(), fromVar, index);
               return;
            }
         }
         toVar.setObject(session, item);
      } else if (obj instanceof List) {
         List<?> list = (List<?>) obj;
         if (list.size() <= index) {
            log.error("#{} Index {} out of bounds: list in {} has {} elements", session.uniqueId(), index, fromVar,
                  list.size());
            return;
         }
         toVar.setObject(session, list.get(index));
      } else {
         log.error("#{} Cannot retrieve item from {} = {}: not an array or list.", session.uniqueId(), fromVar, obj);
      }
   }

   /**
    * Retrieves n-th item from an array or collection.
    */
   @MetaInfServices(Action.Builder.class)
   @Name("getItem")
   public static class Builder implements Action.Builder {
      private String fromVar;
      private IntSourceBuilder<Builder> index = new IntSourceBuilder<>(this);
      private String toVar;

      /**
       * Source variable with an array or list.
       *
       * @param fromVar Variable name.
       * @return Self.
       */
      public Builder fromVar(String fromVar) {
         this.fromVar = fromVar;
         return this;
      }

      /**
       * Source for index into the array/list.
       *
       * @return Builder.
       */
      public IntSourceBuilder<Builder> index() {
         return index;
      }

      /**
       * Destination variable for the n-th element.
       *
       * @param toVar Variable name.
       * @return Self.
       */
      public Builder toVar(String toVar) {
         this.toVar = toVar;
         return this;
      }

      @Override
      public GetItemAction build() {
         return new GetItemAction(SessionFactory.readAccess(fromVar), index.build(), SessionFactory.objectAccess(toVar));
      }
   }
}
