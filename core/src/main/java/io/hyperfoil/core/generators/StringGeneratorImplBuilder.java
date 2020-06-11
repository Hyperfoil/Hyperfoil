package io.hyperfoil.core.generators;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.function.SerializableFunction;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

// To be recognized as a builder by Generator the class name must end with 'Builder'

/**
 * Generic builder for generating a string.
 */
public class StringGeneratorImplBuilder<T> implements StringGeneratorBuilder {
   private static final Logger log = LoggerFactory.getLogger(StringGeneratorImplBuilder.class);

   private final T parent;
   private final boolean urlEncode;
   // We need the Supplier indirection to capture any Access object only during prepareBuild() or build()
   private Supplier<SerializableFunction<Session, String>> supplier;

   public StringGeneratorImplBuilder(T parent, boolean urlEncode) {
      this.parent = parent;
      this.urlEncode = urlEncode;
   }

   private void set(Supplier<SerializableFunction<Session, String>> function) {
      if (this.supplier != null) {
         throw new BenchmarkDefinitionException("Specify only one of: value, var, pattern");
      }
      this.supplier = function;
   }

   /**
    * String value used verbatim.
    *
    * @param value String value.
    * @return Self.
    */
   public StringGeneratorImplBuilder<T> value(String value) {
      if (urlEncode) {
         try {
            String encoded = URLEncoder.encode(value, StandardCharsets.UTF_8.name());
            set(() -> session -> encoded);
         } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
         }
      } else {
         set(() -> session -> value);
      }
      return this;
   }

   /**
    * Load the string from session variable.
    *
    * @param var Variable name.
    * @return Self.
    */
   public StringGeneratorImplBuilder<T> fromVar(String var) {
      boolean urlEncode = this.urlEncode;
      set(() -> {
         Access access = SessionFactory.access(var);
         return session -> {
            Object value = access.getObject(session);
            if (value instanceof String) {
               if (urlEncode) {
                  try {
                     return URLEncoder.encode((String) value, StandardCharsets.UTF_8.name());
                  } catch (UnsupportedEncodingException e) {
                     throw new IllegalStateException(e);
                  }
               }
               return (String) value;
            } else {
               log.error("Cannot retrieve string from {}, the content is {}", var, value);
               return null;
            }
         };
      });
      return this;
   }

   /**
    * Use pattern replacing session variables.
    *
    * @param pattern Template pattern.
    * @return Self.
    */
   public StringGeneratorImplBuilder<T> pattern(String pattern) {
      set(() -> new Pattern(pattern, urlEncode));
      return this;
   }

   public T end() {
      return parent;
   }

   @Override
   public SerializableFunction<Session, String> build() {
      return supplier.get();
   }
}
