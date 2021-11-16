package io.hyperfoil.core.builders;

import java.util.Objects;
import java.util.stream.Stream;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.BuilderBase;
import io.hyperfoil.api.config.InitFromParam;
import io.hyperfoil.api.session.ObjectAccess;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.function.SerializableFunction;

public class ObjectSourceBuilder<P> implements BuilderBase<ObjectSourceBuilder<P>>, InitFromParam<ObjectSourceBuilder<P>> {
   private final P parent;
   private String value;
   private String fromVar;

   public ObjectSourceBuilder(P parent) {
      this.parent = parent;
   }

   /**
    * Verbatim value.
    *
    * @param param String value.
    * @return Self.
    */
   @Override
   public ObjectSourceBuilder<P> init(String param) {
      return value(param);
   }

   /**
    * Verbatim value.
    *
    * @param value String value.
    * @return Self.
    */
   public ObjectSourceBuilder<P> value(String value) {
      this.value = value;
      return this;
   }

   /**
    * Fetch value from session variable.
    *
    * @param fromVar Session variable.
    * @return Self.
    */
   public ObjectSourceBuilder<P> fromVar(String fromVar) {
      this.fromVar = fromVar;
      return this;
   }

   public P end() {
      return parent;
   }

   public SerializableFunction<Session, Object> build() {
      if (Stream.of(value, fromVar).filter(Objects::nonNull).count() != 1) {
         throw new BenchmarkDefinitionException("Must set either 'value' or 'fromVar'");
      }
      if (fromVar != null) {
         return new ValueFromVar(SessionFactory.objectAccess(fromVar));
      } else {
         return new ProvidedValue(value);
      }
   }

   private static class ValueFromVar implements SerializableFunction<Session, Object> {
      private final ObjectAccess fromVar;

      private ValueFromVar(ObjectAccess fromVar) {
         this.fromVar = Objects.requireNonNull(fromVar);
      }

      @Override
      public Object apply(Session session) {
         return fromVar.getObject(session);
      }
   }

   private static class ProvidedValue implements SerializableFunction<Session, Object> {
      private final Object value;

      private ProvidedValue(Object value) {
         this.value = value;
      }

      @Override
      public Object apply(Session session) {
         return value;
      }
   }
}
