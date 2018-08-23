package io.sailrocket.core.machine;

import static java.util.Objects.requireNonNull;

public class SimpleVarReference implements VarReference {
   private final String var;

   public SimpleVarReference(String var) {
      this.var = requireNonNull(var);
   }

   @Override
   public boolean isSet(Session session) {
      return session.isSet(var);
   }
}
