package io.hyperfoil.hotrod.steps;

import io.hyperfoil.api.config.BuilderBase;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.function.SerializableFunction;
import io.hyperfoil.hotrod.api.HotRodOperation;

@FunctionalInterface
public interface HotRodOperationBuilder extends BuilderBase<HotRodOperationBuilder> {
   SerializableFunction<Session, HotRodOperation> build();

   class Provided implements SerializableFunction<Session, HotRodOperation> {
      private final HotRodOperation operation;

      public Provided(HotRodOperation operation) {
         this.operation = operation;
      }

      @Override
      public HotRodOperation apply(Session o) {
         return operation;
      }
   }
}
