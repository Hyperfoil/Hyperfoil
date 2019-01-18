package io.hyperfoil.core.generators;

import java.util.function.Consumer;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.function.SerializableFunction;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class StringGeneratorBuilder<T> {
   private static final Logger log = LoggerFactory.getLogger(StringGeneratorBuilder.class);

   private final T parent;
   private final Consumer<SerializableFunction<Session, String>> consumer;
   private boolean used;

   public StringGeneratorBuilder(T parent, Consumer<SerializableFunction<Session, String>> consumer) {
      this.parent = parent;
      this.consumer = consumer;
   }

   private void ensureUnused() {
      if (used) {
         throw new BenchmarkDefinitionException("Specify only one of: var, pattern");
      }
      used = true;
   }

   public StringGeneratorBuilder<T> var(String var) {
      ensureUnused();
      consumer.accept(session -> {
         Object value = session.getObject(var);
         if (value instanceof String) {
            return (String) value;
         } else {
            log.error("Cannot retrieve string from {}, the content is {}", var, value);
            return null;
         }
      });
      return this;
   }

   public StringGeneratorBuilder<T> pattern(String pattern) {
      ensureUnused();
      consumer.accept(new Pattern(pattern));
      return this;
   }

   public T end() {
      return parent;
   }
}
