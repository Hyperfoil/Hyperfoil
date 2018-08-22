package io.sailrocket.core.machine;

import java.lang.reflect.Array;
import java.util.List;
import java.util.Map;

public class VarReference {
   private final String var;
   private final String indexVar;
   private final String indexMaxVar;

   public VarReference(String var) {
      this(var, null, null);
   }

   public VarReference(String var, String indexVar, String indexMaxVar) {
      this.var = var;
      this.indexVar = indexVar;
      this.indexMaxVar = indexMaxVar;
   }

   public boolean isSet(Session session) {
      if (!session.isSet(var)) return false;
      if (indexVar == null) return true;

      if (!session.isSet(indexVar)) return false;
      int maxIndex = -1;
      if (indexMaxVar != null) {
         if (!session.isSet(indexMaxVar)) return false;
         maxIndex = session.getInt(indexMaxVar);
      }

      Object collection = session.getObject(var);
      if (collection == null) {
         throw new IllegalStateException("Collection in " + var + " is null!");
      } else if (collection.getClass().isArray()) {
         int index = session.getInt(indexVar);
         if (index >= maxIndex) {
            return true;
         }
         return checkVar(collection, index, Array.get(collection, index));
      } else if (collection instanceof List) {
         int index = session.getInt(indexVar);
         if (index >= maxIndex) {
            return true;
         }
         return checkVar(collection, index, ((List) collection).get(index));
      } else if (collection instanceof Map) {
         Object key = session.getInt(indexVar);
         Object o = ((Map) collection).get(key);
         if (o == null) {
            throw new IllegalStateException("There is no mapping for " + var + "/" + key + ": " + collection);
         } else if (o instanceof io.sailrocket.api.Session.Var) {
            return ((io.sailrocket.api.Session.Var) o).isSet();
         } else {
            throw new IllegalStateException("Mapping in " + var + "/" + key + " does not refer to settable variable: " + o);
         }
      } else {
         throw new IllegalStateException("Unknown type to access by index: " + collection);
      }
   }

   private boolean checkVar(Object collection, int index, Object o) {
      if (o instanceof io.sailrocket.api.Session.Var) {
         return ((io.sailrocket.api.Session.Var) o).isSet();
      } else {
         throw new IllegalStateException("Collection in " + var + "[" + indexVar + "=" + index + "] does not contain settable variable: " + o);
      }
   }

   @Override
   public String toString() {
      return indexVar == null ? var : var + "[" + indexVar + "]";
   }
}
