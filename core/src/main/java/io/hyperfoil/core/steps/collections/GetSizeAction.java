package io.hyperfoil.core.steps.collections;

import java.lang.reflect.Array;
import java.util.Collection;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.session.Action;
import io.hyperfoil.api.session.IntAccess;
import io.hyperfoil.api.session.ReadAccess;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.session.SessionFactory;

public class GetSizeAction implements Action {
   private final ReadAccess fromVar;
   private final IntAccess toVar;
   private final int undefinedValue;

   public GetSizeAction(ReadAccess fromVar, IntAccess toVar, int undefinedValue) {
      this.fromVar = fromVar;
      this.toVar = toVar;
      this.undefinedValue = undefinedValue;
   }

   @Override
   public void run(Session session) {
      if (!fromVar.isSet(session)) {
         toVar.setInt(session, undefinedValue);
         return;
      }
      Object obj = fromVar.getObject(session);
      if (obj == null) {
         toVar.setInt(session, undefinedValue);
      } else if (obj.getClass().isArray()) {
         if (Session.Var.class.isAssignableFrom(obj.getClass().getComponentType())) {
            Session.Var[] vars = (Session.Var[]) obj;
            for (int i = vars.length - 1; i >= 0; --i) {
               if (vars[i].isSet()) {
                  toVar.setInt(session, i + 1);
                  return;
               }
            }
            toVar.setInt(session, 0);
         } else {
            toVar.setInt(session, Array.getLength(obj));
         }
      } else if (obj instanceof Collection) {
         toVar.setInt(session, ((Collection<?>) obj).size());
      } else {
         toVar.setInt(session, undefinedValue);
      }
   }

   /**
    * Calculates size of an array/collection held in variable into another variable
    */
   @MetaInfServices(Action.Builder.class)
   @Name("getSize")
   public static class Builder implements Action.Builder {
      private String fromVar;
      private String toVar;
      private int undefinedValue = -1;

      /**
       * Variable holding the collection.
       *
       * @param fromVar Variable holding the collection.
       * @return Self.
       */
      public Builder fromVar(String fromVar) {
         this.fromVar = fromVar;
         return this;
      }

      /**
       * Variable storing the size.
       *
       * @param toVar Variable storing the size.
       * @return Self.
       */
      public Builder toVar(String toVar) {
         this.toVar = toVar;
         return this;
      }

      /**
       * Value to use when <code>fromVar</code> is unset or it does not contain any array/collection.
       *
       * @param undefinedValue Number.
       * @return Self.
       */
      public Builder undefinedValue(int undefinedValue) {
         this.undefinedValue = undefinedValue;
         return this;
      }

      @Override
      public GetSizeAction build() {
         return new GetSizeAction(SessionFactory.readAccess(fromVar), SessionFactory.intAccess(toVar), undefinedValue);
      }
   }
}
