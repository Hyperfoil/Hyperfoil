package io.hyperfoil.core.generators;

import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.InitFromParam;
import io.hyperfoil.api.session.ReadAccess;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.function.SerializableFunction;

// To be recognized as a builder by Generator the class name must end with 'Builder'

/**
 * Generic builder for generating a string.
 */
public class StringGeneratorImplBuilder<T> implements StringGeneratorBuilder, InitFromParam<StringGeneratorImplBuilder<T>> {

   private static final class ConstantPatternSupplier implements Supplier<SerializableFunction<Session, String>> {

      private final Pattern pattern;

      private ConstantPatternSupplier(Pattern pattern) {
         this.pattern = pattern;
      }

      @Override
      public Pattern get() {
         return pattern;
      }
   }

   private static final Logger log = LogManager.getLogger(StringGeneratorImplBuilder.class);

   private final T parent;
   // We need the Supplier indirection to capture any Access object only during prepareBuild() or build()
   private Supplier<SerializableFunction<Session, String>> supplier;

   public StringGeneratorImplBuilder(T parent) {
      this.parent = parent;
   }

   /**
    * @param param A pattern for <a href="https://hyperfoil.io/docs/user-guide/benchmark/variables#string-interpolation">string
    *        interpolation</a>.
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
         ReadAccess access = SessionFactory.readAccess(var);
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

   public boolean isConstantPattern() {
      if (supplier == null) {
         return false;
      }
      if (supplier instanceof StringGeneratorImplBuilder.ConstantPatternSupplier) {
         return ((ConstantPatternSupplier) supplier).get().isConstant();
      }
      return false;
   }

   /**
    * Use <a href="https://hyperfoil.io/docs/user-guide/benchmark/variables#string-interpolation">pattern</a> replacing session
    * variables.
    *
    * @param pattern Template pattern.
    * @return Self.
    */
   public StringGeneratorImplBuilder<T> pattern(String pattern) {
      try {
         set(new ConstantPatternSupplier(new Pattern(pattern, false)));
      } catch (Throwable ignore) {
         log.debug("Cannot parse pattern eagerly '{}': trying lazily", pattern, ignore);
         set(() -> new Pattern(pattern, false));
      }
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
