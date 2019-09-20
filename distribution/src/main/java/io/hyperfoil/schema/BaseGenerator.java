package io.hyperfoil.schema;

import java.lang.reflect.ParameterizedType;

import io.hyperfoil.api.config.ServiceLoadedFactory;
import io.hyperfoil.core.builders.ServiceLoadedBuilderProvider;

class BaseGenerator {
   @SuppressWarnings("unchecked")
   static Iterable<ServiceLoadedFactory<?>> getFactories(Class<? extends ServiceLoadedFactory<?>> factoryClass) {
      return ServiceLoadedBuilderProvider.factories((Class) factoryClass);
   }

   static Class<? extends ServiceLoadedFactory<?>> getBuilderFactoryClass(java.lang.reflect.Type builderFactory) {
      Class<? extends ServiceLoadedFactory<?>> bfClass;
      if (builderFactory instanceof Class) {
         bfClass = (Class<? extends ServiceLoadedFactory<?>>) builderFactory;
      } else if (builderFactory instanceof ParameterizedType){
         bfClass = (Class<? extends ServiceLoadedFactory<?>>) ((ParameterizedType) builderFactory).getRawType();
      } else {
         throw new IllegalStateException("Cannot analyze factory type " + builderFactory);
      }
      return bfClass;
   }
}
