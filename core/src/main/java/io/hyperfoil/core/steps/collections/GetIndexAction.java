package io.hyperfoil.core.steps.collections;

import java.util.List;
import java.util.Objects;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.session.Action;
import io.hyperfoil.api.session.IntAccess;
import io.hyperfoil.api.session.ReadAccess;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.builders.ObjectSourceBuilder;
import io.hyperfoil.core.session.ObjectVar;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.function.SerializableFunction;

public class GetIndexAction implements Action {
   private final ReadAccess collection;
   private final SerializableFunction<Session, Object> item;
   private final IntAccess toVar;

   public GetIndexAction(ReadAccess collection, SerializableFunction<Session, Object> item, IntAccess toVar) {
      this.collection = collection;
      this.item = item;
      this.toVar = toVar;
   }

   @Override
   public void run(Session session) {
      Object collection = this.collection.getObject(session);
      Object item = this.item.apply(session);
      if (collection instanceof ObjectVar[]) {
         ObjectVar[] vars = (ObjectVar[]) collection;
         for (int i = 0; i < vars.length && vars[i].isSet(); ++i) {
            if (Objects.equals(item, vars[i].objectValue(session))) {
               toVar.setInt(session, i);
               return;
            }
         }
         toVar.setInt(session, -1);
      } else if (collection instanceof Object[]) {
         Object[] items = (Object[]) collection;
         for (int i = 0; i < items.length; ++i) {
            if (Objects.equals(item, items[i])) {
               toVar.setInt(session, i);
               return;
            }
         }
      } else if (collection instanceof List) {
         List<?> list = (List<?>) collection;
         for (int i = 0; i < list.size(); ++i) {
            if (Objects.equals(item, list.get(i))) {
               toVar.setInt(session, i);
               return;
            }
         }
      } else {
         throw new IllegalArgumentException("Cannot lookup " + item + " in " + collection + ": unsupported collection type.");
      }
   }

   /**
    * Lookup index of an item in an array/collection.
    */
   @MetaInfServices(Action.Builder.class)
   @Name("getIndex")
   public static class Builder implements Action.Builder {
      private String collection;
      private ObjectSourceBuilder<Builder> item = new ObjectSourceBuilder<>(this);
      private String toVar;

      /**
       * Variable to fetch the collection from.
       *
       * @param collection Session variable name.
       * @return Self.
       */
      public Builder collection(String collection) {
         this.collection = collection;
         return this;
      }

      /**
       * Item that should be looked up (using equality).
       *
       * @return Builder.
       */
      public ObjectSourceBuilder<Builder> item() {
         return item;
      }

      /**
       * Integer variable to store the index, or -1 if the item is not found.
       *
       * @param toVar Session variable name.
       * @return Self.
       */
      public Builder toVar(String toVar) {
         this.toVar = toVar;
         return this;
      }

      @Override
      public GetIndexAction build() {
         return new GetIndexAction(SessionFactory.readAccess(collection), item.build(), SessionFactory.intAccess(toVar));
      }
   }
}
