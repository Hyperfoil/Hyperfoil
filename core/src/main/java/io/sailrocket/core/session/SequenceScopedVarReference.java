package io.sailrocket.core.session;

import java.lang.reflect.Array;
import java.util.List;

import io.sailrocket.api.VarReference;
import io.sailrocket.api.Session;

public class SequenceScopedVarReference implements VarReference {
   private final String var;

   public SequenceScopedVarReference(String var) {
      this.var = var;
   }

   @Override
   public boolean isSet(Session session) {
      if (!session.isSet(var)) return false;

      Object collection = session.getObject(var);
      if (collection == null) {
         throw new IllegalStateException("Collection in " + var + " is null!");
      } else if (collection.getClass().isArray()) {
         int index = session.currentSequence().index();
         return checkVar(index, Array.get(collection, index));
      } else if (collection instanceof List) {
         int index = session.currentSequence().index();
         return checkVar(index, ((List) collection).get(index));
      } else {
         throw new IllegalStateException("Unknown type to access by index: " + collection);
      }
   }

   private boolean checkVar(int index, Object o) {
      if (o instanceof io.sailrocket.api.Session.Var) {
         return ((io.sailrocket.api.Session.Var) o).isSet();
      } else {
         throw new IllegalStateException("Collection in " + var + "[" + index + "] does not contain settable variable: " + o);
      }
   }

   @Override
   public String toString() {
      return var + "[currentSequence]";
   }
}
