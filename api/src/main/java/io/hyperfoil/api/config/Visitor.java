package io.hyperfoil.api.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Type;

public interface Visitor {
   boolean visit(String name, Object value, Type fieldType);

   @Retention(RetentionPolicy.RUNTIME)
   @Target(ElementType.FIELD)
   @interface Ignore {
   }

   @Retention(RetentionPolicy.RUNTIME)
   @Target(ElementType.FIELD)
   @interface Invoke {
      String method();
   }
}
