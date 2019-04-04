package io.hyperfoil.core.parser;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.stream.Stream;

import org.yaml.snakeyaml.events.Event;
import org.yaml.snakeyaml.events.MappingEndEvent;
import org.yaml.snakeyaml.events.MappingStartEvent;
import org.yaml.snakeyaml.events.ScalarEvent;
import org.yaml.snakeyaml.events.SequenceEndEvent;
import org.yaml.snakeyaml.events.SequenceStartEvent;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.ListBuilder;
import io.hyperfoil.api.config.MappingListBuilder;
import io.hyperfoil.api.config.PairBuilder;
import io.hyperfoil.api.config.PartialBuilder;
import io.hyperfoil.api.config.ServiceLoadedContract;
import io.hyperfoil.api.config.BaseSequenceBuilder;
import io.hyperfoil.core.builders.StepCatalog;
import io.hyperfoil.core.steps.ServiceLoadedBuilderProvider;

class StepParser implements Parser<BaseSequenceBuilder> {
   private static final StepParser INSTANCE = new StepParser();

   private static Method selectMethod(Method m1, Method m2) {
      if (m1 == null) {
         return m2;
      } else if (m2 == null) {
         return m1;
      }
      boolean m1Primitive = hasPrimitiveParams(m1);
      boolean m2Primitive = hasPrimitiveParams(m2);
      if (m1Primitive && m2Primitive) {
         return m1.getParameterCount() <= m2.getParameterCount() ? m1 : m2;
      } else if (m1Primitive) {
         return m1;
      } else if (m2Primitive) {
         return m2;
      } else {
         return null;
      }
   }

   private static boolean hasPrimitiveParams(Method m1) {
      for (Class<?> param : m1.getParameterTypes()) {
         if (!param.isPrimitive() && param != String.class && !param.isEnum()) {
            return false;
         }
      }
      return true;
   }

   public static StepParser instance() {
      return INSTANCE;
   }

   private StepParser() {}

   @Override
   public void parse(Context ctx, BaseSequenceBuilder target) throws ParserException {
      Event firstEvent = ctx.next();
      StepCatalog catalog = target.step(StepCatalog.class);
      if (firstEvent instanceof ScalarEvent) {
         ScalarEvent stepEvent = (ScalarEvent) firstEvent;
         Object builder = invokeWithNoParams(catalog, stepEvent, stepEvent.getValue());
         if (builder instanceof ServiceLoadedContract) {
            ((ServiceLoadedContract<?>) builder).complete();
         }
         return;
      } else if (!(firstEvent instanceof MappingStartEvent)) {
         throw ctx.unexpectedEvent(firstEvent);
      }

      ScalarEvent stepEvent = ctx.expectEvent(ScalarEvent.class);
      if (!ctx.hasNext()) {
         throw ctx.noMoreEvents(ScalarEvent.class, MappingStartEvent.class, MappingEndEvent.class, SequenceStartEvent.class);
      }
      invokeWithParameters(ctx, catalog, stepEvent);
      ctx.expectEvent(MappingEndEvent.class);
   }

   private void invokeWithParameters(Context ctx, Object target, ScalarEvent keyEvent) throws ParserException {
      Event defEvent = ctx.peek();
      String key = keyEvent.getValue();
      if (defEvent instanceof MappingEndEvent) {
         // end of list item -> no arg step
         Object builder = invokeWithNoParams(target, keyEvent, key);
         if (builder instanceof ServiceLoadedContract) {
            ((ServiceLoadedContract<?>) builder).complete();
         }
         // we'll expect the mapping end at the end
      } else if (defEvent instanceof ScalarEvent) {
         // - step : param syntax
         String value = ((ScalarEvent) defEvent).getValue();
         if (value != null && !value.isEmpty()) {
            invokeWithSingleParam(target, keyEvent, key, defEvent, value);
         } else {
            Object builder = invokeWithNoParams(target, keyEvent, key);
            if (builder instanceof ServiceLoadedContract) {
               ((ServiceLoadedContract<?>) builder).complete();
            }
         }
         ctx.consumePeeked(defEvent);
      } else if (defEvent instanceof MappingStartEvent) {
         Object builder = invokeWithDefaultParams(target, keyEvent, key);
         ctx.consumePeeked(defEvent);
         if (builder instanceof ServiceLoadedContract) {
            ServiceLoadedContract<?> serviceLoadedContract = (ServiceLoadedContract<?>) builder;
            applyMapping(ctx, serviceLoadedContract.builder());
            serviceLoadedContract.complete();
         } else if (builder instanceof ServiceLoadedBuilderProvider) {
            defEvent = ctx.next();
            if (defEvent instanceof MappingEndEvent) {
               throw new ParserException(defEvent, "Expecting builder type but found end of mapping.");
            } else if (defEvent instanceof ScalarEvent) {
               String name = ((ScalarEvent) defEvent).getValue();
               ServiceLoadedBuilderProvider<?, ?> provider = (ServiceLoadedBuilderProvider<?, ?>) builder;
               ServiceLoadedContract<?> slc;
               Event builderEvent = ctx.next();
               String param = null;
               if (builderEvent instanceof ScalarEvent) {
                  param = ((ScalarEvent) builderEvent).getValue();
               }
               try {
                  slc = provider.forName(name, param);
               } catch (BenchmarkDefinitionException e) {
                  throw new ParserException(defEvent, "Failed to instantiate service-loaded builder " + name, e);
               }
               if (builderEvent instanceof MappingStartEvent) {
                  applyMapping(ctx, slc.builder());
               } else if (!(builderEvent instanceof ScalarEvent)) {
                  throw ctx.unexpectedEvent(builderEvent);
               }
               slc.complete();
            } else {
               throw ctx.unexpectedEvent(defEvent);
            }
            ctx.expectEvent(MappingEndEvent.class);
         } else {
            if (builder instanceof MappingListBuilder) {
               // MappingListBuilder allows specifying single mapping directly without embedding into a list
               builder = ((MappingListBuilder) builder).addItem();
            }
            applyMapping(ctx, builder);
         }
      } else if (defEvent instanceof SequenceStartEvent) {
         Object builder = invokeWithNoParams(target, keyEvent, key);
         ServiceLoadedContract<?> slc = null;
         if (builder instanceof ServiceLoadedContract) {
            slc = (ServiceLoadedContract<?>) builder;
            builder = slc.builder();
         }
         if (builder instanceof BaseSequenceBuilder) {
            ctx.parseList((BaseSequenceBuilder) builder, StepParser.instance());
         } else {
            ctx.consumePeeked(defEvent);
            MappingListBuilder<?> mlb = null;
            if (builder instanceof MappingListBuilder) {
               mlb = (MappingListBuilder<?>) builder;
            }
            while (ctx.hasNext()) {
               defEvent = ctx.next();
               if (defEvent instanceof SequenceEndEvent) {
                  break;
               } else if (mlb != null) {
                  builder = mlb.addItem();
               }
               if (defEvent instanceof ScalarEvent) {
                  if (builder instanceof ListBuilder) {
                     ((ListBuilder) builder).nextItem(((ScalarEvent) defEvent).getValue());
                  } else {
                     invokeWithParameters(ctx, builder, (ScalarEvent) defEvent);
                  }
               } else if (defEvent instanceof MappingStartEvent) {
                  applyMapping(ctx, builder);
               } else {
                  throw ctx.unexpectedEvent(defEvent);
               }
            }
         }
         if (slc != null) {
            slc.complete();
         }
      }
   }

   private void invokeWithSingleParam(Object target, ScalarEvent keyEvent, String key, Event valueEvent, String value) throws ParserException {
      if (target instanceof PairBuilder) {
         PairBuilder builder = (PairBuilder) target;
         acceptPair(builder, key, value, valueEvent);
         return;
      }
      Result<Method> result = findMethod(keyEvent, target, key, 1);
      if (result.value != null) {
         Object param = convert(valueEvent, value, result.value.getParameterTypes()[0]);
         try {
            result.value.invoke(target, param);
         } catch (IllegalAccessException | InvocationTargetException e) {
            throw cannotCreate(keyEvent, e);
         }
      } else if (target instanceof StepCatalog){
         getLoadedBuilder((StepCatalog) target, keyEvent, key, value, result.exception).complete();
      } else {
         throw result.exception;
      }
   }

   @SuppressWarnings("unchecked")
   private void acceptPair(PairBuilder builder, String key, String value, Event valueEvent) throws ParserException {
      Object param = convert(valueEvent, value, builder.valueType());
      builder.accept(key, param);
   }

   private ServiceLoadedContract<?> getLoadedBuilder(StepCatalog target, ScalarEvent keyEvent, String key, String value, ParserException exception) throws ParserException {
      ServiceLoadedContract<?> serviceLoadedContract;
      try {
         serviceLoadedContract = target.serviceLoaded().forName(key, value);
      } catch (BenchmarkDefinitionException e) {
         ParserException pe = new ParserException(keyEvent, "Cannot find any step matching name '" + key + "'", e);
         pe.addSuppressed(exception);
         throw pe;
      }
      return serviceLoadedContract;
   }

   private Object invokeWithDefaultParams(Object target, ScalarEvent keyEvent, String key) throws ParserException {
      if (target instanceof PartialBuilder) {
         return ((PartialBuilder) target).withKey(key);
      }
      Result<Method> result = findMethod(keyEvent, target, key, -1);
      if (result.value != null) {
         Method method = result.value;
         Object[] args = new Object[method.getParameterCount()];
         Class<?>[] parameterTypes = method.getParameterTypes();
         for (int i = 0; i < parameterTypes.length; i++) {
            args[i] = defaultValue(parameterTypes[i]);
         }
         try {
            return method.invoke(target, args);
         } catch (IllegalAccessException | InvocationTargetException e) {
            throw cannotCreate(keyEvent, e);
         }
      } else if (target instanceof StepCatalog) {
         return getLoadedBuilder((StepCatalog) target, keyEvent, key, null, result.exception);
      } else {
         throw result.exception;
      }
   }

   private Object invokeWithNoParams(Object target, ScalarEvent keyEvent, String key) throws ParserException {
      if (target instanceof PartialBuilder) {
         return ((PartialBuilder) target).withKey(key);
      }
      Result<Method> result = findMethod(keyEvent, target, key, 0);
      if (result.value != null) {
         try {
            return result.value.invoke(target);
         } catch (IllegalAccessException | InvocationTargetException e) {
            throw cannotCreate(keyEvent, e);
         }
      } else if (target instanceof StepCatalog) {
         return getLoadedBuilder((StepCatalog) target, keyEvent, key, null, result.exception);
      } else {
         throw result.exception;
      }
   }

   private void applyMapping(Context ctx, Object builder) throws ParserException {
      while (ctx.hasNext()) {
         Event event = ctx.next();
         if (event instanceof MappingEndEvent) {
            break;
         } else if (event instanceof ScalarEvent) {
            invokeWithParameters(ctx, builder, (ScalarEvent) event);
         } else {
            throw ctx.unexpectedEvent(event);
         }
      }
   }

   private Result<Method> findMethod(Event event, Object target, String name, int params) {
      Method[] candidates = Stream.of(target.getClass().getMethods()).filter(m -> m.getName().equals(name)).toArray(Method[]::new);
      if (candidates.length == 0) {
         return new Result<>(new ParserException(event, "Cannot find method '" + name + "' on '" + target + "'"));
      } else if (params >= 0) {
         return Stream.of(candidates).filter(m -> m.getParameterCount() == params).findAny().map(Result::new)
               .orElseGet(() -> new Result<>(new ParserException(event, "Wrong number of parameters to '" + name + "', expecting " + params)));
      } else {
         return new Result<>(Stream.of(candidates).reduce(StepParser::selectMethod).get());
      }
   }

   private Object convert(Event event, String str, Class<?> type) throws ParserException {
      if (type == String.class) {
         return str;
      } else if (type == boolean.class) {
         return Boolean.parseBoolean(str);
      } else if (type == int.class) {
         return Integer.parseInt(str);
      } else if (type == long.class) {
         return Long.parseLong(str);
      } else if (type == double.class) {
         return Double.parseDouble(str);
      } else if (type.isEnum()) {
         return parseEnum(str, type);
      } else {
         throw new ParserException(event, "Cannot convert " + str + " to " + type);
      }
   }

   @SuppressWarnings("unchecked")
   private Enum parseEnum(String str, Class<?> type) {
      return Enum.valueOf( (Class<Enum>) type, str);
   }

   private Object defaultValue(Class<?> clazz) {
      return Array.get(Array.newInstance(clazz, 1), 0);
   }

   private ParserException cannotCreate(ScalarEvent event, Exception exception) {
      return new ParserException(event, "Cannot create step/builder '" + event.getValue() + "'", exception);
   }

   private static class Result<T> {
      final T value;
      final ParserException exception;

      private Result(T value) {
         this.value = value;
         this.exception = null;
      }

      private Result(ParserException exception) {
         this.value = null;
         this.exception = exception;
      }
   }
}
