package io.hyperfoil.core.steps;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.function.Consumer;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.Locator;
import io.hyperfoil.api.config.ServiceLoadedContract;
import io.hyperfoil.api.config.ServiceLoadedFactory;

public class ServiceLoadedBuilderProvider<B> {
   private static final Map<Class<ServiceLoadedFactory<?>>, ServiceLoader<ServiceLoadedFactory<?>>> SERVICE_LOADERS = new HashMap<>();

   private final Class<? extends ServiceLoadedFactory<B>> factoryClazz;
   private final Locator locator;
   private final Consumer<B> consumer;

   public static Iterable<ServiceLoadedFactory<?>> factories(Class<ServiceLoadedFactory<?>> clazz) {
      ServiceLoader<ServiceLoadedFactory<?>> loader = SERVICE_LOADERS.get(clazz);
      if (loader == null) {
         loader = ServiceLoader.load(clazz);
         SERVICE_LOADERS.put(clazz, loader);
      }
      return loader;
   }

   private static ServiceLoadedFactory<?> factory(Class<ServiceLoadedFactory<?>> clazz, String name) {
      Iterable<ServiceLoadedFactory<?>> loader = factories(clazz);
      ServiceLoadedFactory<?> factory = null;
      for (ServiceLoadedFactory<?> f : loader) {
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

   public ServiceLoadedBuilderProvider(Class<? extends ServiceLoadedFactory<B>> factoryClazz, Locator locator, Consumer<B> consumer) {
      this.factoryClazz = factoryClazz;
      this.locator = locator;
      this.consumer = consumer;
   }

   public ServiceLoadedContract<B> forName(String name, String param) {
      ServiceLoadedFactory<B> factory = factory((Class) factoryClazz, name);
      if (param != null && !factory.acceptsParam()) {
         throw new BenchmarkDefinitionException(factory.name() + " does not accept inline parameter");
      }
      B builder = factory.newBuilder(locator, param);
      return new ServiceLoadedContract<>(builder, consumer);
   }
}
