package io.hyperfoil.core.steps.collections;

import java.util.Objects;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.session.Action;
import io.hyperfoil.api.session.ReadAccess;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.session.ObjectVar;
import io.hyperfoil.core.session.SessionFactory;

public class AddItemAction implements Action {
   private static final Logger log = LogManager.getLogger(AddItemAction.class);

   private final ReadAccess fromVar;
   private final String value;
   // We don't use ObjectAccess because a) we don't need it b) we need someone else to initialize the array
   private final ReadAccess toVar;

   public AddItemAction(ReadAccess fromVar, String value, ReadAccess toVar) {
      this.fromVar = fromVar;
      this.value = value;
      this.toVar = toVar;
   }

   @Override
   public void run(Session session) {
      if (fromVar != null && !fromVar.isSet(session)) {
         log.error("#{}: Source variable {} is not set", session.uniqueId(), fromVar);
         return;
      }
      if (!toVar.isSet(session)) {
         log.error("#{}: Destination variable {} is not set (should contain array)", session.uniqueId(), fromVar);
         return;
      }
      Object item = fromVar == null ? value : fromVar.getObject(session);
      Object dest = toVar.getObject(session);
      if (dest instanceof ObjectVar[]) {
         ObjectVar[] array = (ObjectVar[]) dest;
         for (int i = 0; i < array.length; ++i) {
            if (!array[i].isSet()) {
               array[i].set(item);
               return;
            }
         }
         log.warn("#{} The array in variable {} is full ({} items), cannot add {}", session.uniqueId(), toVar, array.length, item);
      } else {
         log.error("#{} Variable {} should contain ObjectVar array but it contains {}", session.uniqueId(), toVar, dest);
      }
   }

   /**
    * Appends value to an array stored in another variable.
    */
   @MetaInfServices(Action.Builder.class)
   @Name("addItem")
   public static class Builder implements Action.Builder {
      private String fromVar;
      private String value;
      private String toVar;

      /**
       * Source variable with the item.
       *
       * @param fromVar Variable name.
       * @return Self.
       */
      public Builder fromVar(String fromVar) {
         this.fromVar = fromVar;
         return this;
      }

      /**
       * Verbatim value to be used as the appended item.
       *
       * @param value Arbitrary string.
       * @return Self.
       */
      public Builder value(String value) {
         this.value = value;
         return this;
      }

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
         if (Stream.of(fromVar, value).filter(Objects::nonNull).count() != 1) {
            throw new BenchmarkDefinitionException("addItem requires exactly one of these properties to be set: fromVar, value");
         }
         return new AddItemAction(SessionFactory.readAccess(fromVar), value, SessionFactory.readAccess(toVar));
      }
   }
}
