package io.hyperfoil.core.builders;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Consumer;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.Locator;
import io.hyperfoil.api.config.ServiceLoadedContract;
import io.hyperfoil.api.config.ServiceLoadedFactory;

public class ServiceLoadedBuilderProvider<B, BF extends ServiceLoadedFactory<B>> {
   private static final Map<Class<? extends ServiceLoadedFactory<?>>, ServiceLoader<? extends ServiceLoadedFactory<?>>> SERVICE_LOADERS = new HashMap<>();
   private static final Map<Class<ServiceLoadedFactory<?>>, Collection<ServiceLoadedFactory<?>>> FACTORIES = new HashMap<>();

   private final Class<BF> factoryClazz;
   private final Locator locator;
   private final Consumer<B> consumer;

   public static synchronized Iterable<ServiceLoadedFactory<?>> factories(Class<ServiceLoadedFactory<?>> clazz) {
      Collection<ServiceLoadedFactory<?>> factories = FACTORIES.get(clazz);
      if (factories != null) {
         return factories;
      }
      Set<Class<? extends ServiceLoadedFactory<?>>> included = new HashSet<>();
      ArrayDeque<Class<? extends ServiceLoadedFactory<?>>> deque = new ArrayDeque<>();
      deque.add(clazz);
      included.add(clazz);
      factories = new ArrayList<>();
      while (!deque.isEmpty()) {
         Class<? extends ServiceLoadedFactory<?>> factoryClass = deque.poll();
         ServiceLoader<? extends ServiceLoadedFactory<?>> loader = SERVICE_LOADERS.get(factoryClass);
         if (loader == null) {
            loader = ServiceLoader.load(factoryClass);
            SERVICE_LOADERS.put(factoryClass, loader);
         }
         for (ServiceLoadedFactory<?> factory : loader) {
            factories.add(factory);
         }
         ServiceLoadedFactory.Include include = factoryClass.getAnnotation(ServiceLoadedFactory.Include.class);
         if (include != null) {
            for (Class<? extends ServiceLoadedFactory<?>> incl : include.value()) {
               if (!included.contains(incl)) {
                  deque.add(incl);
               }
            }
         }
      }
      FACTORIES.put(clazz, factories);
      return factories;
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

   public ServiceLoadedBuilderProvider(Class<BF> factoryClazz, Locator locator, Consumer<B> consumer) {
      this.factoryClazz = factoryClazz;
      this.locator = locator;
      this.consumer = consumer;
   }

   public ServiceLoadedContract<B> forName(String name, String param) {
      @SuppressWarnings("unchecked")
      ServiceLoadedFactory<B> factory = factory((Class) factoryClazz, name);
      if (param != null && !param.isEmpty() && !factory.acceptsParam()) {
         throw new BenchmarkDefinitionException(factory.name() + " does not accept inline parameter");
      }
      B builder = factory.newBuilder(locator, param);
      return new ServiceLoadedContract<>(builder, consumer);
   }

   public interface Owner<B, BF extends ServiceLoadedFactory<B>> {
      ServiceLoadedBuilderProvider<B, BF> serviceLoaded();
   }
}
