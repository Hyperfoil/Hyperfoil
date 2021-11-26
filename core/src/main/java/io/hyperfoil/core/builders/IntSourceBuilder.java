package io.hyperfoil.core.builders;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.BuilderBase;
import io.hyperfoil.api.config.InitFromParam;
import io.hyperfoil.api.config.MappingListBuilder;
import io.hyperfoil.api.session.ReadAccess;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.function.SerializableToIntFunction;

public class IntSourceBuilder<P> implements BuilderBase<IntSourceBuilder<P>>, InitFromParam<IntSourceBuilder<P>> {
   private final P parent;
   private Integer value;
   private String fromVar;

   public IntSourceBuilder(P parent) {
      this.parent = parent;
   }

   public P end() {
      return parent;
   }

   /**
    * @param param Uses the argument as a constant value.
    * @return Self.
    */
   @Override
   public IntSourceBuilder<P> init(String param) {
      try {
         return value(Integer.parseInt(param));
      } catch (NumberFormatException e) {
         throw new BenchmarkDefinitionException("Cannot parse value '" + param + "' as an integer.");
      }
   }

   /**
    * Value (integer).
    *
    * @param value Value.
    * @return Self.
    */
   public IntSourceBuilder<P> value(int value) {
      this.value = value;
      return this;
   }

   /**
    * Input variable name.
    *
    * @param fromVar Input variable name.
    * @return Self.
    */
   public IntSourceBuilder<P> fromVar(String fromVar) {
      this.fromVar = fromVar;
      return this;
   }

   public SerializableToIntFunction<Session> build() {
      if (Stream.of(value, fromVar).filter(Objects::nonNull).count() != 1) {
         throw new BenchmarkDefinitionException("Must set either 'value' or 'fromVar'");
      }
      if (value != null) {
         return new ProvidedValue(value);
      } else {
         return new ValueFromVar(SessionFactory.readAccess(fromVar));
      }
   }

   public static class ListBuilder implements MappingListBuilder<IntSourceBuilder<Void>>, BuilderBase<IntSourceBuilder<Void>> {
      private final List<IntSourceBuilder<Void>> list = new ArrayList<>();

      @Override
      public IntSourceBuilder<Void> addItem() {
         IntSourceBuilder<Void> item = new IntSourceBuilder<>(null);
         list.add(item);
         return item;
      }

      @SuppressWarnings("unchecked")
      public SerializableToIntFunction<Session>[] build() {
         return list.stream().map(IntSourceBuilder::build).toArray(SerializableToIntFunction[]::new);
      }
   }

   public static class ProvidedValue implements SerializableToIntFunction<Session> {
      private final int value;

      public ProvidedValue(int value) {
         this.value = value;
      }

      @Override
      public int applyAsInt(Session session) {
         return value;
      }
   }

   public static class ValueFromVar implements SerializableToIntFunction<Session> {
      private final ReadAccess fromVar;

      public ValueFromVar(ReadAccess fromVar) {
         this.fromVar = fromVar;
      }

      @Override
      public int applyAsInt(Session session) {
         Session.Var var = fromVar.getVar(session);
         if (var.type() == Session.VarType.INTEGER) {
            return fromVar.getInt(session);
         } else {
            Object value = fromVar.getObject(session);
            if (value instanceof String) {
               return Integer.parseInt((String) value);
            } else {
               throw new IllegalStateException("Cannot implicitly convert " + value + " to integer.");
            }
         }
      }
   }
}
