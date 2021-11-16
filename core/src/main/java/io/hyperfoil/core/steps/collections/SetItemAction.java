package io.hyperfoil.core.steps.collections;

import org.kohsuke.MetaInfServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.Embed;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.session.Action;
import io.hyperfoil.api.session.ReadAccess;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.builders.IntSourceBuilder;
import io.hyperfoil.core.builders.ObjectSourceBuilder;
import io.hyperfoil.core.session.ObjectVar;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.function.SerializableFunction;
import io.hyperfoil.function.SerializableToIntFunction;

public class SetItemAction implements Action {
   private static final Logger log = LoggerFactory.getLogger(SetItemAction.class);

   private final SerializableFunction<Session, Object> item;
   private final SerializableToIntFunction<Session> index;
   private final ReadAccess toVar;

   public SetItemAction(SerializableFunction<Session, Object> item, SerializableToIntFunction<Session> index, ReadAccess toVar) {
      this.item = item;
      this.index = index;
      this.toVar = toVar;
   }

   @Override
   public void run(Session session) {
      if (!toVar.isSet(session)) {
         log.error("#{}: Destination variable {} is not set (should contain array)", session.uniqueId(), toVar);
         return;
      }
      Object item = this.item.apply(session);
      Object dest = toVar.getObject(session);
      int index = this.index.applyAsInt(session);
      if (dest instanceof ObjectVar[]) {
         ObjectVar[] vars = (ObjectVar[]) dest;
         if (index < 0) {
            throw new IllegalArgumentException("The index " + this.index + " = " + index + " is negative!");
         } else if (index >= vars.length) {
            throw new IllegalArgumentException("The index " + this.index + " = " + index + " exceeds collection size (" + vars.length + ")!");
         }
         vars[index].set(item);
      } else {
         log.error("#{} Variable {} should contain ObjectVar array but it contains {}", session.uniqueId(), toVar, dest);
      }
   }

   /**
    * Set element in a collection on given position.
    */
   @MetaInfServices(Action.Builder.class)
   @Name("setItem")
   public static class Builder implements Action.Builder {
      @Embed
      public ObjectSourceBuilder<Builder> item = new ObjectSourceBuilder<>(this);
      private String toVar;
      private IntSourceBuilder<Builder> index = new IntSourceBuilder<>(this);

      /**
       * Session variable with the collection.
       *
       * @param toVar Variable name.
       * @return Self.
       */
      public Builder toVar(String toVar) {
         this.toVar = toVar;
         return this;
      }

      /**
       * Integer session variable with an index into the collection.
       *
       * @return Builder.
       */
      public IntSourceBuilder<Builder> index() {
         return index;
      }

      @Override
      public SetItemAction build() {
         if (toVar == null) {
            throw new BenchmarkDefinitionException("Property 'toVar' must be set!");
         } else if (index == null) {
            throw new BenchmarkDefinitionException("Property 'index' must be set!");
         }
         return new SetItemAction(item.build(), index.build(), SessionFactory.readAccess(toVar));
      }
   }
}
