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

public class IntSourceBuilder implements BuilderBase<IntSourceBuilder>, InitFromParam<IntSourceBuilder> {
   private Integer value;
   private String fromVar;

   @Override
   public IntSourceBuilder init(String param) {
      return value(Integer.parseInt(param));
   }

   /**
    * Value (integer).
    *
    * @param value Value.
    * @return Self.
    */
   public IntSourceBuilder value(int value) {
      this.value = value;
      return this;
   }

   /**
    * Input variable name.
    *
    * @param fromVar Input variable name.
    * @return Self.
    */
   public IntSourceBuilder fromVar(String fromVar) {
      this.fromVar = fromVar;
      return this;
   }

   public SerializableToIntFunction<Session> build() {
      if (Stream.of(value, fromVar).filter(Objects::nonNull).count() != 1) {
         throw new BenchmarkDefinitionException("Must set either 'value' or 'fromVar'");
      }
      if (value != null) {
         int capture = value;
         return session -> capture;
      } else if (fromVar != null) {
         ReadAccess access = SessionFactory.readAccess(fromVar);
         return access::getInt;
      }
      throw new IllegalStateException();
   }

   public static class ListBuilder implements MappingListBuilder<IntSourceBuilder>, BuilderBase<IntSourceBuilder> {
      private final List<IntSourceBuilder> list = new ArrayList<>();

      @Override
      public IntSourceBuilder addItem() {
         IntSourceBuilder item = new IntSourceBuilder();
         list.add(item);
         return item;
      }

      public SerializableToIntFunction<Session>[] build() {
         //noinspection unchecked
         return list.stream().map(IntSourceBuilder::build).toArray(SerializableToIntFunction[]::new);
      }
   }
}
