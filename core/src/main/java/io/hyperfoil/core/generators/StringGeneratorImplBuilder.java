package io.hyperfoil.core.generators;

import java.util.function.Supplier;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.InitFromParam;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.function.SerializableFunction;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

// To be recognized as a builder by Generator the class name must end with 'Builder'

/**
 * Generic builder for generating a string.
 */
public class StringGeneratorImplBuilder<T> implements StringGeneratorBuilder, InitFromParam<StringGeneratorImplBuilder<T>> {
   private static final Logger log = LogManager.getLogger(StringGeneratorImplBuilder.class);

   private final T parent;
   // We need the Supplier indirection to capture any Access object only during prepareBuild() or build()
   private Supplier<SerializableFunction<Session, String>> supplier;

   public StringGeneratorImplBuilder(T parent) {
      this.parent = parent;
   }

   /**
    * @param param A pattern for <a href="https://hyperfoil.io/userguide/benchmark/variables.html#string-interpolation">string interpolation</a>.
    * @return Self.
    */
   @Override
   public StringGeneratorImplBuilder<T> init(String param) {
      return pattern(param);
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
      set(() -> session -> value);
      return this;
   }

   /**
    * Load the string from session variable.
    *
    * @param var Variable name.
    * @return Self.
    */
   public StringGeneratorImplBuilder<T> fromVar(Object var) {
      if (var == null) {
         throw new BenchmarkDefinitionException("Variable must not be null");
      }
      set(() -> {
         Access access = SessionFactory.access(var);
         assert access != null;
         return session -> {
            Object value = access.getObject(session);
            if (value instanceof String) {
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
    * Use <a href="https://hyperfoil.io/userguide/benchmark/variables.html#string-interpolation">pattern</a> replacing session variables.
    *
    * @param pattern Template pattern.
    * @return Self.
    */
   public StringGeneratorImplBuilder<T> pattern(String pattern) {
      set(() -> new Pattern(pattern, false));
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
