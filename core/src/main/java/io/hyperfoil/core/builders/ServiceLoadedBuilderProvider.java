package io.hyperfoil.core.builders;

import java.lang.reflect.Constructor;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import io.hyperfoil.api.config.BaseSequenceBuilder;
import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.IncludeBuilders;
import io.hyperfoil.api.config.InitFromParam;
import io.hyperfoil.api.config.Name;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class ServiceLoadedBuilderProvider<B> {
   private static final Logger log = LoggerFactory.getLogger(ServiceLoadedBuilderProvider.class);

   private static final Map<Class<?>, Map<String, BuilderInfo<?>>> BUILDERS = new ConcurrentHashMap<>();

   private final Class<B> builderClazz;
   private final Consumer<B> consumer;
   private final BaseSequenceBuilder parent;

   public static synchronized Map<String, BuilderInfo<?>> builders(Class<?> clazz) {
      return BUILDERS.computeIfAbsent(clazz, ServiceLoadedBuilderProvider::scanBuilders);
   }

   private static Map<String, BuilderInfo<?>> scanBuilders(Class<?> clazz) {
      Map<String, BuilderInfo<?>> builders = new HashMap<>();
      Set<Class<?>> included = new HashSet<>();
      ArrayDeque<BuilderInfo<Object>> deque = new ArrayDeque<>();
      deque.add(new BuilderInfo<>(clazz, Function.identity()));
      included.add(clazz);
      while (!deque.isEmpty()) {
         BuilderInfo<Object> builderInfo = deque.poll();
         ServiceLoader.load(builderInfo.implClazz).stream().forEach(provider -> {
            Name name = provider.type().getAnnotation(Name.class);
            if (name == null || name.value().isEmpty()) {
               log.error("Service-loaded class {} is missing @Name annotation!", provider.type());
            } else {
               // Collisions may exist, e.g. due to different chains of adapters. First match
               // (the first in breadth-first search, so the closest) wins.
               builders.putIfAbsent(name.value(), new BuilderInfo<>(provider.type(), builderInfo.adapter));
            }
         });
         IncludeBuilders include = builderInfo.implClazz.getAnnotation(IncludeBuilders.class);
         if (include != null) {
            for (IncludeBuilders.Conversion conversion : include.value()) {
               if (!included.contains(conversion.from())) {
                  try {
                     @SuppressWarnings("unchecked")
                     Function<Object, Object> adapter = (Function<Object, Object>) conversion.adapter().getDeclaredConstructor().newInstance();
                     // Since we use transitive inclusions through adapters, we have to chain adapters into each other
                     deque.add(new BuilderInfo<>(conversion.from(), builder -> builderInfo.adapter.apply(adapter.apply(builder))));
                  } catch (Exception e) {
                     throw new IllegalStateException("Cannot instantiate " + conversion.adapter());
                  }
               }
            }
         }
      }
      return builders;
   }

   public ServiceLoadedBuilderProvider(Class<B> builderClazz, Consumer<B> consumer) {
      this(builderClazz, consumer, null);
   }

   public ServiceLoadedBuilderProvider(Class<B> builderClazz, Consumer<B> consumer, BaseSequenceBuilder parent) {
      this.builderClazz = builderClazz;
      this.consumer = consumer;
      this.parent = parent;
   }

   public ServiceLoadedContract forName(String name, String param) {
      @SuppressWarnings("unchecked")
      BuilderInfo<B> builderInfo = (BuilderInfo<B>) builders(builderClazz).get(name);
      if (builderInfo == null) {
         throw new BenchmarkDefinitionException(String.format("No builder implementing %s with @Name %s", builderClazz, name));
      }
      try {
         Object instance = newInstance(builderInfo);
         if (param != null && !param.isEmpty()) {
            if (instance instanceof InitFromParam) {
               ((InitFromParam) instance).init(param);
            } else {
               throw new BenchmarkDefinitionException(name + "(" + builderInfo.implClazz + ") cannot be initialized from an inline parameter");
            }
         }
         return new ServiceLoadedContract(instance, () -> consumer.accept(builderInfo.adapter.apply(instance)));
      } catch (Exception e) {
         throw new BenchmarkDefinitionException("Failed to instantiate " + builderInfo.implClazz, e);
      }
   }

   private Object newInstance(BuilderInfo<B> builderInfo) throws InstantiationException, IllegalAccessException, java.lang.reflect.InvocationTargetException {
      if (parent != null) {
         Constructor<?> parentCtor = Stream.of(builderInfo.implClazz.getDeclaredConstructors())
               .filter(ctor -> ctor.getParameterCount() == 1 && ctor.getParameterTypes()[0] == BaseSequenceBuilder.class)
               .findFirst().orElse(null);
         if (parentCtor != null) {
            return parentCtor.newInstance(parent);
         }
      }
      Constructor<?> noArgCtor = Stream.of(builderInfo.implClazz.getDeclaredConstructors())
            .filter(ctor -> ctor.getParameterCount() == 0).findFirst().orElse(null);
      if (noArgCtor == null) {
         throw new BenchmarkDefinitionException("Class " + builderInfo.implClazz.getName() + " does not have a parameterless constructor.");
      }
      return noArgCtor.newInstance();
   }

   public interface Owner<B> {
      ServiceLoadedBuilderProvider<B> serviceLoaded();
   }

}
