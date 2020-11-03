package io.hyperfoil.core.builders;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.BuilderBase;
import io.hyperfoil.api.config.MappingListBuilder;
import io.hyperfoil.api.session.Session;

public class AllConditions implements Condition {
   private final Condition[] conditions;

   public AllConditions(Condition[] conditions) {
      assert conditions != null;
      assert conditions.length > 0;
      this.conditions = conditions;
   }

   @Override
   public boolean test(Session session) {
      for (Condition c : conditions) {
         if (!c.test(session)) {
            return false;
         }
      }
      return true;
   }

   public static class Builder<P> implements MappingListBuilder<Condition.Builder<?>>, BuilderBase<Builder<P>>, Condition.Builder<Builder<P>> {
      private final P parent;
      private final List<TypesBuilder<Builder<P>>> list = new ArrayList<>();

      public Builder() {
         this(null);
      }

      public Builder(P parent) {
         this.parent = parent;
      }

      @Override
      public TypesBuilder<Builder<P>> addItem() {
         TypesBuilder<Builder<P>> builder = new TypesBuilder<>(this);
         list.add(builder);
         return builder;
      }

      public P end() {
         return parent;
      }

      public AllConditions buildCondition() {
         if (list.isEmpty()) {
            throw new BenchmarkDefinitionException("Condition list is empty!");
         }
         return new AllConditions(list.stream().map(TypesBuilder::buildCondition).filter(Objects::nonNull).toArray(Condition[]::new));
      }
   }
}
