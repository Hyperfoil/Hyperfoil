package io.hyperfoil.core.steps.collections;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.session.Action;
import io.hyperfoil.api.session.ReadAccess;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.builders.IntSourceBuilder;
import io.hyperfoil.core.session.ObjectVar;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.function.SerializableToIntFunction;

public class RemoveItemAction implements Action {
   private final ReadAccess fromVar;
   private final SerializableToIntFunction<Session> index;

   public RemoveItemAction(ReadAccess fromVar, SerializableToIntFunction<Session> index) {
      this.fromVar = fromVar;
      this.index = index;
   }

   @Override
   public void run(Session session) {
      Object value = fromVar.getObject(session);
      if (value instanceof ObjectVar[]) {
         ObjectVar[] vars = (ObjectVar[]) value;
         int index = this.index.applyAsInt(session);
         int lastIndex = index;
         for (int i = index + 1; i < vars.length && vars[i].isSet(); ++i) {
            vars[i - 1].set(vars[i].objectValue(session));
            lastIndex = i;
         }
         vars[lastIndex].unset();
      } else {
         session.fail(new IllegalStateException("Variable " + fromVar + " should contain a writable array."));
      }
   }

   /**
    * Removes element from an array of variables.
    */
   @MetaInfServices(Action.Builder.class)
   @Name("removeItem")
   public static class Builder implements Action.Builder {
      private String fromVar;
      private final IntSourceBuilder<Builder> index = new IntSourceBuilder<>(this);

      /**
       * Variable containing an existing array of object variables.
       *
       * @param fromVar Variable name.
       * @return Self.
       */
      public Builder fromVar(String fromVar) {
         this.fromVar = fromVar;
         return this;
      }

      /**
       * Set index at which the item should be removed. Elements to the right of this
       * are moved to the left.
       *
       * @return Builder.
       */
      public IntSourceBuilder<Builder> index() {
         return index;
      }

      @Override
      public RemoveItemAction build() {
         if (fromVar == null) {
            throw new BenchmarkDefinitionException("Property 'fromVar' must be set!");
         }
         return new RemoveItemAction(SessionFactory.readAccess(fromVar), index.build());
      }
   }
}
