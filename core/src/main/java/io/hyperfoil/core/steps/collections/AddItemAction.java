package io.hyperfoil.core.steps.collections;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.Embed;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.session.Action;
import io.hyperfoil.api.session.ReadAccess;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.builders.ObjectSourceBuilder;
import io.hyperfoil.core.session.ObjectVar;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.function.SerializableFunction;

public class AddItemAction implements Action {
   private static final Logger log = LogManager.getLogger(AddItemAction.class);

   private final SerializableFunction<Session, Object> item;
   // We don't use ObjectAccess because a) we don't need it b) we need someone else to initialize the array
   private final ReadAccess toVar;

   public AddItemAction(SerializableFunction<Session, Object> item, ReadAccess toVar) {
      this.item = item;
      this.toVar = toVar;
   }

   @Override
   public void run(Session session) {
      if (!toVar.isSet(session)) {
         throw new IllegalStateException("Destination variable " + toVar + " is not set (should contain array)");
      }
      Object item = this.item.apply(session);
      Object dest = toVar.getObject(session);
      if (dest instanceof ObjectVar[]) {
         ObjectVar[] array = (ObjectVar[]) dest;
         for (int i = 0; i < array.length; ++i) {
            if (!array[i].isSet()) {
               array[i].set(item);
               return;
            }
         }
         log.warn("#{} The array in variable {} is full ({} items), cannot add {}", session.uniqueId(), toVar, array.length,
               item);
      } else {
         throw new IllegalStateException("Variable " + toVar + " should contain ObjectVar array but it contains " + dest);
      }
   }

   /**
    * Appends value to an array stored in another variable.
    */
   @MetaInfServices(Action.Builder.class)
   @Name("addItem")
   public static class Builder implements Action.Builder {
      private String toVar;
      @Embed
      public ObjectSourceBuilder<Builder> item = new ObjectSourceBuilder<>(this);

      /**
       * Destination variable with the array.
       *
       * @param toVar Variable name.
       * @return Self.
       */
      public Builder toVar(String toVar) {
         this.toVar = toVar;
         return this;
      }

      @Override
      public AddItemAction build() {
         return new AddItemAction(item.build(), SessionFactory.readAccess(toVar));
      }
   }
}
