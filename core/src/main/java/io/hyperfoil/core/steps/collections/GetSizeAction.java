package io.hyperfoil.core.steps.collections;

import java.lang.reflect.Array;
import java.util.Collection;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
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
   private final BoolFilter boolFilter;
   private final StringFilter stringFilter;
   private final IntFilter intFilter;

   public GetSizeAction(ReadAccess fromVar, IntAccess toVar, int undefinedValue, BoolFilter boolFilter, StringFilter stringFilter, IntFilter intFilter) {
      this.fromVar = fromVar;
      this.toVar = toVar;
      this.undefinedValue = undefinedValue;
      this.boolFilter = boolFilter;
      this.stringFilter = stringFilter;
      this.intFilter = intFilter;
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
            int size = 0;
            for (int i = 0; i < vars.length; ++i) {
               if (!vars[i].isSet()) {
                  break;
               } else if (testVar(session, vars[i])) {
                  ++size;
               }
            }
            toVar.setInt(session, size);
         } else {
            int length = Array.getLength(obj);
            if (boolFilter == null && stringFilter == null && intFilter == null) {
               toVar.setInt(session, length);
            } else {
               int size = 0;
               for (int i = 0; i < length; ++i) {
                  if (testItem(session, Array.get(obj, i))) {
                     ++size;
                  }
               }
               toVar.setInt(session, size);
            }
         }
      } else if (obj instanceof Collection) {
         Collection<?> collection = (Collection<?>) obj;
         int length = collection.size();
         if (boolFilter == null && stringFilter == null && intFilter == null) {
            toVar.setInt(session, length);
         } else {
            int size = 0;
            // TODO: don't allocate iterator for Lists?
            for (Object item : collection) {
               if (testItem(session, item)) {
                  ++size;
               }
            }
            toVar.setInt(session, size);
         }
      } else {
         toVar.setInt(session, undefinedValue);
      }
   }

   private boolean testVar(Session session, Session.Var var) {
      return (boolFilter == null || boolFilter.testVar(session, var)) &&
            (stringFilter == null || stringFilter.testVar(session, var)) &&
            (intFilter == null || intFilter.testVar(session, var));
   }

   private boolean testItem(Session session, Object item) {
      return (boolFilter == null || boolFilter.testObject(item)) &&
            (stringFilter == null || stringFilter.testObject(session, item)) &&
            (intFilter == null || intFilter.testObject(session, item));
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
      private BoolFilter.Builder<Builder> boolFilter;
      private StringFilter.Builder<Builder> stringFilter;
      private IntFilter.Builder<Builder> intFilter;

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
       * Count only items matching the condition.
       *
       * @return Builder.
       */
      public BoolFilter.Builder<Builder> boolFilter() {
         if (boolFilter != null) {
            throw new BenchmarkDefinitionException("Bool filter already set!");
         }
         return boolFilter = new BoolFilter.Builder<>(this);
      }

      /**
       * Count only items matching the condition.
       *
       * @return Builder.
       */
      public StringFilter.Builder<Builder> stringFilter() {
         if (stringFilter != null) {
            throw new BenchmarkDefinitionException("String filter already set!");
         }
         return stringFilter = new StringFilter.Builder<>(this);
      }

      /**
       * Count only items matching the condition.
       *
       * @return Builder.
       */
      public IntFilter.Builder<Builder> intFilter() {
         if (intFilter != null) {
            throw new BenchmarkDefinitionException("Integer filter already set!");
         }
         return intFilter = new IntFilter.Builder<>(this);
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
         return new GetSizeAction(SessionFactory.readAccess(fromVar), SessionFactory.intAccess(toVar), undefinedValue,
               boolFilter == null ? null : boolFilter.build(),
               stringFilter == null ? null : stringFilter.build(),
               intFilter == null ? null : intFilter.build());
      }
   }
}
