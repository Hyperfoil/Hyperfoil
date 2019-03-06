package io.hyperfoil.core.session;

import java.lang.reflect.Array;
import java.util.List;

import io.hyperfoil.api.session.VarReference;
import io.hyperfoil.api.session.Session;

public class SequenceScopedVarReference implements VarReference {
   private final String var;
   private final boolean set;

   public SequenceScopedVarReference(String var) {
      if (var.startsWith("!")) {
         this.var = var.substring(1).trim();
         this.set = false;
      } else {
         this.var = var;
         this.set = true;
      }
   }

   @Override
   public boolean isSet(Session session) {
      if (set && !session.isSet(var)) return false;

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
      if (o instanceof Session.Var) {
         return ((Session.Var) o).isSet() == set;
      } else {
         throw new IllegalStateException("Collection in " + var + "[" + index + "] does not contain settable variable: " + o);
      }
   }

   @Override
   public String toString() {
      return (set ? "" : "<not set>") + var + "[currentSequence]";
   }
}
