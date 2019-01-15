package io.hyperfoil.core.session;

import static java.util.Objects.requireNonNull;

import io.hyperfoil.api.session.VarReference;
import io.hyperfoil.api.session.Session;

public class SimpleVarReference implements VarReference {
   private final String var;

   public SimpleVarReference(String var) {
      this.var = requireNonNull(var);
   }

   @Override
   public boolean isSet(Session session) {
      return session.isSet(var);
   }

   @Override
   public String toString() {
      return "SimpleVarReference(" + var + ')';
   }
}
