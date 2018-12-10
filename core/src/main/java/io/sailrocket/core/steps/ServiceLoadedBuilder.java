package io.sailrocket.core.steps;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.function.Consumer;

import io.sailrocket.api.config.BenchmarkDefinitionException;
import io.sailrocket.api.config.LoadedBuilder;

public class ServiceLoadedBuilder<T> {
   private static final Map<Class<LoadedBuilder.Factory<?>>, ServiceLoader<LoadedBuilder.Factory<?>>> SERVICE_LOADERS = new HashMap<>();

   private final Class<? extends LoadedBuilder.Factory<T>> factoryClazz;
   private final Consumer<T> consumer;

   private static LoadedBuilder.Factory<?> factory(Class<LoadedBuilder.Factory<?>> clazz, String name) {
      ServiceLoader<LoadedBuilder.Factory<?>> loader = SERVICE_LOADERS.get(clazz);
      if (loader == null) {
         loader = ServiceLoader.load(clazz);
         SERVICE_LOADERS.put(clazz, loader);
      }
      LoadedBuilder.Factory<?> factory = null;
      for (LoadedBuilder.Factory<?> f : loader) {
         if (f.name().equals(name)) {
            if (factory != null) {
               throw new BenchmarkDefinitionException("Two classes ('" + factory.getClass().getName() +
                     "' and '" + f.getClass().getName() + "') provide builders for name '" + name + "' and type '" +
                     clazz.getName() + "'");
            }
            factory = f;
         }
      }
      if (factory == null) {
         throw new BenchmarkDefinitionException("No class provides builders for name '" + name + "' and type '" + clazz.getName() + "'");
      }
      return factory;
   }


   public ServiceLoadedBuilder(Class<? extends LoadedBuilder.Factory<T>> factoryClazz, Consumer<T> consumer) {
      this.factoryClazz = factoryClazz;
      this.consumer = consumer;
   }

   public LoadedBuilder forName(String name) {
      LoadedBuilder builder = factory((Class) factoryClazz, name).newBuilder(consumer);
      return builder;
   }
}
