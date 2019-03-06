package io.hyperfoil.core.session;

import io.hyperfoil.api.session.VarReference;
import io.hyperfoil.api.session.Session;

public class SimpleVarReference implements VarReference {
   private final String var;
   private final boolean set;

   public SimpleVarReference(String var) {
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
      return session.isSet(var) == set;
   }

   @Override
   public String toString() {
      return "SimpleVarReference(" + var + ')';
   }
}
