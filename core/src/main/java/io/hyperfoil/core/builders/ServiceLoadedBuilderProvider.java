package io.hyperfoil.core.builders;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.BuilderBase;
import io.hyperfoil.api.config.IncludeBuilders;
import io.hyperfoil.api.config.InitFromParam;
import io.hyperfoil.api.config.Locator;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.config.ServiceLoadedContract;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class ServiceLoadedBuilderProvider<B> {
   private static final Logger log = LoggerFactory.getLogger(ServiceLoadedBuilderProvider.class);

   private static final Map<Class<?>, Map<String, BuilderInfo<?>>> BUILDERS = new HashMap<>();

   private final Class<B> builderClazz;
   private final Locator locator;
   private final Consumer<B> consumer;

   public static synchronized Map<String, BuilderInfo<?>> builders(Class<?> clazz) {
      Map<String, BuilderInfo<?>> builders = BUILDERS.get(clazz);
      if (builders != null) {
         return builders;
      }
      builders = new HashMap<>();
      Set<Class<?>> included = new HashSet<>();
      ArrayDeque<BuilderInfo<Object>> deque = new ArrayDeque<>();
      deque.add(new BuilderInfo<>(clazz, Function.identity()));
      included.add(clazz);
      while (!deque.isEmpty()) {
         BuilderInfo<Object> builderInfo = deque.poll();
         // TODO: once we rebase on JDK 9+ we could user ServiceLoader.stream to inspect without instantiation
         for (Object builder : ServiceLoader.load(builderInfo.implClazz)) {
            Name name = builder.getClass().getAnnotation(Name.class);
            if (name == null || name.value().isEmpty()) {
               log.error("Service-loaded class {} is missing @Name annotation!", builder.getClass());
            } else {
               builders.put(name.value(), new BuilderInfo<>(builder.getClass(), builderInfo.adapter));
            }
         }
         IncludeBuilders include = builderInfo.implClazz.getAnnotation(IncludeBuilders.class);
         if (include != null) {
            for (IncludeBuilders.Conversion conversion : include.value()) {
               if (!included.contains(conversion.from())) {
                  try {
                     @SuppressWarnings("unchecked")
                     Function<Object, Object> adapter = (Function<Object, Object>) conversion.adapter().getDeclaredConstructor().newInstance();
                     deque.add(new BuilderInfo<>(conversion.from(), adapter));
                  } catch (Exception e) {
                     throw new IllegalStateException("Cannot instantiate " + conversion.adapter());
                  }
               }
            }
         }
      }
      BUILDERS.put(clazz, builders);
      return builders;
   }

   public ServiceLoadedBuilderProvider(Class<B> builderClazz, Locator locator, Consumer<B> consumer) {
      this.builderClazz = builderClazz;
      this.locator = locator;
      this.consumer = consumer;
   }

   public ServiceLoadedContract forName(String name, String param) {
      @SuppressWarnings("unchecked")
      BuilderInfo<B> builderInfo = (BuilderInfo<B>) builders(builderClazz).get(name);
      if (builderInfo == null) {
         throw new BenchmarkDefinitionException(String.format("No builder implementing %s with @Name %s", builderClazz, name));
      }
      try {
         Object instance = builderInfo.implClazz.getDeclaredConstructor().newInstance();
         if (instance instanceof BuilderBase) {
            ((BuilderBase) instance).setLocator(locator);
         }
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

   public interface Owner<B> {
      ServiceLoadedBuilderProvider<B> serviceLoaded();
   }

}
