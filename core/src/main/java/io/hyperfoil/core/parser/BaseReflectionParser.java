package io.hyperfoil.core.parser;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.yaml.snakeyaml.events.Event;
import org.yaml.snakeyaml.events.MappingEndEvent;
import org.yaml.snakeyaml.events.MappingStartEvent;
import org.yaml.snakeyaml.events.ScalarEvent;
import org.yaml.snakeyaml.events.SequenceEndEvent;
import org.yaml.snakeyaml.events.SequenceStartEvent;

import io.hyperfoil.api.config.BaseSequenceBuilder;
import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.Embed;
import io.hyperfoil.api.config.InitFromParam;
import io.hyperfoil.api.config.ListBuilder;
import io.hyperfoil.api.config.MappingListBuilder;
import io.hyperfoil.api.config.PairBuilder;
import io.hyperfoil.api.config.PartialBuilder;
import io.hyperfoil.core.builders.ServiceLoadedBuilderProvider;
import io.hyperfoil.core.builders.ServiceLoadedContract;
import io.hyperfoil.impl.Util;

public class BaseReflectionParser {

   protected void invokeWithParameters(Context ctx, Object target, ScalarEvent keyEvent) throws ParserException {
      Event defEvent = ctx.peek();
      String key = keyEvent.getValue();
      if (defEvent instanceof MappingEndEvent) {
         // end of list item -> no arg step
         Object builder = invokeWithNoParams(target, keyEvent, key);
         if (builder instanceof ServiceLoadedContract) {
            ((ServiceLoadedContract) builder).complete();
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
               ((ServiceLoadedContract) builder).complete();
            }
         }
         ctx.consumePeeked(defEvent);
      } else if (defEvent instanceof MappingStartEvent) {
         Object builder = invokeWithNoParams(target, keyEvent, key);
         ctx.consumePeeked(defEvent);
         if (builder instanceof ServiceLoadedContract) {
            ServiceLoadedContract serviceLoadedContract = (ServiceLoadedContract) builder;
            applyMapping(ctx, serviceLoadedContract.builder());
            serviceLoadedContract.complete();
         } else if (builder instanceof ServiceLoadedBuilderProvider) {
            ScalarEvent nameEvent = ctx.expectEvent(ScalarEvent.class);
            fillSLBP(ctx, nameEvent, (ServiceLoadedBuilderProvider<?>) builder);
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
         ServiceLoadedContract slc = null;
         if (builder instanceof ServiceLoadedContract) {
            slc = (ServiceLoadedContract) builder;
            builder = slc.builder();
         }
         if (builder instanceof BaseSequenceBuilder) {
            ctx.parseList((BaseSequenceBuilder) builder, StepParser.instance());
         } else if (builder instanceof ServiceLoadedBuilderProvider) {
            ctx.consumePeeked(defEvent);
            while (ctx.hasNext()) {
               Event itemEvent = ctx.next();
               if (itemEvent instanceof SequenceEndEvent) {
                  break;
               } else if (itemEvent instanceof ScalarEvent) {
                  String name = ((ScalarEvent) itemEvent).getValue();
                  ServiceLoadedBuilderProvider<?> provider = (ServiceLoadedBuilderProvider<?>) builder;
                  try {
                     provider.forName(name, null).complete();
                  } catch (BenchmarkDefinitionException e) {
                     throw new ParserException(itemEvent, "Failed to instantiate service-loaded builder " + name, e);
                  }
               } else if (itemEvent instanceof MappingStartEvent) {
                  ScalarEvent nameEvent = ctx.expectEvent(ScalarEvent.class);
                  fillSLBP(ctx, nameEvent, (ServiceLoadedBuilderProvider<?>) builder);
                  ctx.expectEvent(MappingEndEvent.class);
               } else {
                  throw ctx.unexpectedEvent(defEvent);
               }
            }
         } else {
            ctx.consumePeeked(defEvent);
            MappingListBuilder<?> mlb = null;
            if (builder instanceof MappingListBuilder) {
               mlb = (MappingListBuilder<?>) builder;
            }
            while (ctx.hasNext()) {
               Event itemEvent = ctx.next();
               if (itemEvent instanceof SequenceEndEvent) {
                  break;
               } else if (mlb != null) {
                  builder = mlb.addItem();
               }
               if (itemEvent instanceof ScalarEvent) {
                  if (builder instanceof ListBuilder) {
                     ((ListBuilder) builder).nextItem(((ScalarEvent) itemEvent).getValue());
                  } else {
                     invokeWithParameters(ctx, builder, (ScalarEvent) itemEvent);
                  }
               } else if (itemEvent instanceof MappingStartEvent) {
                  applyMapping(ctx, builder);
               } else {
                  throw ctx.unexpectedEvent(itemEvent);
               }
            }
         }
         if (slc != null) {
            slc.complete();
         }
      }
   }

   protected void fillSLBP(Context ctx, ScalarEvent nameEvent, ServiceLoadedBuilderProvider<?> provider)
         throws ParserException {
      ServiceLoadedContract slc;
      Event builderEvent = ctx.next();
      String param = null;
      if (builderEvent instanceof ScalarEvent) {
         param = ((ScalarEvent) builderEvent).getValue();
      }
      try {
         slc = provider.forName(nameEvent.getValue(), param);
      } catch (BenchmarkDefinitionException e) {
         throw new ParserException(nameEvent, "Failed to instantiate service-loaded builder " + nameEvent.getValue(), e);
      }
      if (builderEvent instanceof MappingStartEvent) {
         applyMapping(ctx, slc.builder());
      } else if (builderEvent instanceof ScalarEvent) {
         // already handled as param
      } else {
         throw ctx.unexpectedEvent(builderEvent);
      }
      slc.complete();
   }

   private void invokeWithSingleParam(Object target, ScalarEvent keyEvent, String key, Event valueEvent, String value)
         throws ParserException {
      if (target instanceof PairBuilder) {
         PairBuilder builder = (PairBuilder) target;
         acceptPair(builder, key, value, valueEvent);
         return;
      }
      Invocable invocable = findMethod(keyEvent, target, key, 1);
      if (invocable.method != null) {
         try {
            if (invocable.method.getParameterCount() == 1) {
               Object param = convert(valueEvent, value, invocable.method.getParameterTypes()[0]);
               invocable.method.invoke(invocable.targetSupplier.get(), param);
            } else {
               assert invocable.method.getParameterCount() == 0;
               Object builder = invocable.method.invoke(invocable.targetSupplier.get());
               ((InitFromParam) builder).init(value);
            }
         } catch (IllegalAccessException | InvocationTargetException e) {
            throw cannotCreate(keyEvent, e);
         }
      } else if (target instanceof ServiceLoadedBuilderProvider.Owner) {
         getLoadedBuilder((ServiceLoadedBuilderProvider.Owner<?>) target, keyEvent, key, value, invocable.exception).complete();
      } else {
         throw invocable.exception;
      }
   }

   @SuppressWarnings("unchecked")
   private void acceptPair(PairBuilder builder, String key, String value, Event valueEvent) throws ParserException {
      Object param = convert(valueEvent, value, builder.valueType());
      builder.accept(key, param);
   }

   private ServiceLoadedContract getLoadedBuilder(ServiceLoadedBuilderProvider.Owner<?> target, ScalarEvent keyEvent,
         String key, String value, ParserException exception) throws ParserException {
      ServiceLoadedContract serviceLoadedContract;
      try {
         serviceLoadedContract = target.serviceLoaded().forName(key, value);
      } catch (BenchmarkDefinitionException e) {
         ParserException pe = new ParserException(keyEvent, "Cannot find any step matching name '" + key + "'", e);
         pe.addSuppressed(exception);
         throw pe;
      }
      return serviceLoadedContract;
   }

   protected Object invokeWithNoParams(Object target, ScalarEvent keyEvent, String key) throws ParserException {
      if (target instanceof PartialBuilder) {
         return ((PartialBuilder) target).withKey(key);
      }
      Invocable invocable = findMethod(keyEvent, target, key, 0);
      if (invocable.method != null) {
         try {
            return invocable.method.invoke(invocable.targetSupplier.get());
         } catch (IllegalAccessException | InvocationTargetException e) {
            throw cannotCreate(keyEvent, e);
         }
      } else if (target instanceof ServiceLoadedBuilderProvider.Owner) {
         return getLoadedBuilder((ServiceLoadedBuilderProvider.Owner<?>) target, keyEvent, key, null, invocable.exception);
      } else {
         throw invocable.exception;
      }
   }

   protected void applyMapping(Context ctx, Object builder) throws ParserException {
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

   private Invocable findMethod(Event event, Object target, String name, int params) {
      List<Invocable> matchingName = new ArrayList<>();
      Queue<Invocable> todo = new ArrayDeque<>();
      Set<Class<?>> visited = new HashSet<>();
      todo.add(new Invocable(target.getClass(), () -> target, null, 0));
      while (!todo.isEmpty()) {
         Invocable inv = todo.poll();
         visited.add(inv.builderType);
         for (Method m : inv.builderType.getMethods()) {
            if (m.getName().equals(name)) {
               matchingName.add(new Invocable(inv.builderType, inv.targetSupplier, m, inv.depth + 1));
            }
         }
         for (Field f : inv.builderType.getFields()) {
            if (f.isAnnotationPresent(Embed.class)) {
               // ideally the field should be final as well but we won't enforce that
               if (visited.contains(f.getType()) || f.getType().isPrimitive() || Modifier.isStatic(f.getModifiers())) {
                  continue;
               }
               todo.add(new Invocable(f.getType(), () -> {
                  try {
                     return f.get(inv.targetSupplier.get());
                  } catch (IllegalAccessException e) {
                     throw new BenchmarkDefinitionException("Cannot access " + inv.builderType.getName() + "." + f.getName(),
                           e);
                  }
               }, null, inv.depth + 1));
            }
         }
         for (Method m : inv.builderType.getMethods()) {
            if (m.isAnnotationPresent(Embed.class)) {
               if (visited.contains(m.getReturnType()) || m.getParameterCount() > 0 || Modifier.isStatic(m.getModifiers())) {
                  continue;
               }
               todo.add(new Invocable(m.getReturnType(), () -> {
                  try {
                     return m.invoke(inv.targetSupplier.get());
                  } catch (IllegalAccessException | InvocationTargetException e) {
                     throw new BenchmarkDefinitionException("Cannot access " + inv.builderType.getName() + "." + m.getName(),
                           e);
                  }
               }, null, inv.depth + 1));
            }
         }
      }

      Invocable[] candidates = matchingName.stream()
            .filter(inv -> inv.method.getParameterCount() == params)
            .filter(inv -> Stream.of(inv.method.getParameterTypes()).allMatch(Util::isParamConvertible))
            .toArray(Invocable[]::new);
      if (params == 1 && candidates.length == 0) {
         candidates = matchingName.stream().filter(inv -> InitFromParam.class.isAssignableFrom(inv.method.getReturnType()))
               .toArray(Invocable[]::new);
      }
      if (candidates.length == 0) {
         return new Invocable(new ParserException(event, "Cannot find method '" + name + "' on '" + target + "'"));
      } else {
         int lowestDepth = Arrays.stream(candidates).mapToInt(c -> c.depth).min().orElse(0);
         candidates = Arrays.stream(candidates).filter(c -> c.depth == lowestDepth).toArray(Invocable[]::new);
      }
      if (candidates.length == 1) {
         return candidates[0];
      } else { // candidates.length > 1
         return new Invocable(new ParserException(event,
               "Ambiguous candidates for '" + name + "' on '" + target + "': " + Arrays.asList(candidates)));
      }
   }

   private Object convert(Event event, String str, Class<?> type) throws ParserException {
      if (type == String.class || type == CharSequence.class || type == Object.class) {
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
      return Enum.valueOf((Class<Enum>) type, str);
   }

   private ParserException cannotCreate(ScalarEvent event, Exception exception) {
      return new ParserException(event, "Cannot create step/builder '" + event.getValue() + "'", exception);
   }

   private static class Invocable {
      final Class<?> builderType;
      final Supplier<Object> targetSupplier;
      final Method method;
      final ParserException exception;
      final int depth;

      private Invocable(Class<?> builderType, Supplier<Object> targetSupplier, Method method, int depth) {
         this.builderType = builderType;
         this.targetSupplier = targetSupplier;
         this.method = method;
         this.depth = depth;
         this.exception = null;
      }

      private Invocable(ParserException exception) {
         this.builderType = null;
         this.targetSupplier = null;
         this.method = null;
         this.exception = exception;
         this.depth = 0;
      }

      @Override
      public String toString() {
         if (exception != null) {
            return exception.toString();
         } else if (builderType == null || method == null) {
            return "<null>";
         }
         return builderType.getName() + "." + method.getName() + "(" + Arrays.toString(method.getParameterTypes()) + ")("
               + depth + ")";
      }
   }
}
