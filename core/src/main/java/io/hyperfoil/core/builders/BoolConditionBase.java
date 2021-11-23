package io.hyperfoil.core.builders;

import java.io.Serializable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.hyperfoil.api.session.Session;
import io.hyperfoil.impl.Util;

public abstract class BoolConditionBase implements Serializable {
   private static final Logger log = LogManager.getLogger(BoolConditionBase.class);

   protected final boolean value;

   public BoolConditionBase(boolean value) {
      this.value = value;
   }

   public boolean testVar(Session session, Session.Var var) {
      if (!var.isSet()) {
         return false;
      }
      if (var.type() == Session.VarType.INTEGER) {
         // this is not C - we won't convert 1 to true, 0 to false
         return false;
      } else if (var.type() == Session.VarType.OBJECT) {
         Object obj = var.objectValue(session);
         return testObject(obj);
      } else {
         throw new IllegalStateException("Unknown type of var: " + var);
      }
   }

   public boolean testObject(Object obj) {
      if (obj instanceof Boolean) {
         log.trace("Test boolean {} == {}", obj, value);
         return (boolean) obj == value;
      } else if (obj instanceof CharSequence) {
         CharSequence str = (CharSequence) obj;
         log.trace("Test string {} equals {}", str, value);
         if (value) {
            return Util.regionMatchesIgnoreCase(str, 0, "true", 0, 4);
         } else {
            return Util.regionMatchesIgnoreCase(str, 0, "false", 0, 5);
         }
      }
      return false;
   }
}
