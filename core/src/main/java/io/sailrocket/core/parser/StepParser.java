package io.sailrocket.core.parser;

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

import io.sailrocket.api.config.LoadedBuilder;
import io.sailrocket.core.builders.BaseSequenceBuilder;
import io.sailrocket.core.builders.SequenceBuilder;
import io.sailrocket.core.builders.StepDiscriminator;
import io.sailrocket.core.steps.ServiceLoadedBuilder;

class StepParser implements Parser<BaseSequenceBuilder> {
   private static final StepParser INSTANCE = new StepParser();

   private static Method selectMethod(Method m1, Method m2) {
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
      StepDiscriminator discriminator = target.step();
      if (firstEvent instanceof ScalarEvent) {
         ScalarEvent stepEvent = (ScalarEvent) firstEvent;
         invokeWithNoArgs(discriminator, stepEvent, stepEvent.getValue());
         return;
      } else if (!(firstEvent instanceof MappingStartEvent)) {
         throw ctx.unexpectedEvent(firstEvent);
      }

      ScalarEvent stepEvent = ctx.expectEvent(ScalarEvent.class);
      if ("sla".equals(stepEvent.getValue())) {
         if (target instanceof SequenceBuilder) {
            SLAParser.instance().parse(ctx, ((SequenceBuilder) target).sla());
         } else {
            throw new ParserException(stepEvent, "SLAs are allowed only as the top-level sequence element.");
         }
         ctx.expectEvent(MappingEndEvent.class);
         return;
      }
      if (!ctx.hasNext()) {
         throw ctx.noMoreEvents(ScalarEvent.class, MappingStartEvent.class, MappingEndEvent.class, SequenceStartEvent.class);
      }
      invokeWithParameters(ctx, discriminator, stepEvent);
      ctx.expectEvent(MappingEndEvent.class);
   }

   private void invokeWithParameters(Context ctx, Object target, ScalarEvent keyEvent) throws ParserException {
      Event defEvent = ctx.peek();
      String key = keyEvent.getValue();
      if (defEvent instanceof MappingEndEvent) {
         // end of list item -> no arg step
         invokeWithNoArgs(target, keyEvent, key);
         // we'll expect the mapping end at the end
      } else if (defEvent instanceof ScalarEvent) {
         // - step : param syntax
         String value = ((ScalarEvent) defEvent).getValue();
         if (value != null && !value.isEmpty()) {
            Method method = findMethod(keyEvent, target, key, 1);
            Object param = convert(defEvent, value, method.getParameterTypes()[0]);
            try {
               method.invoke(target, param);
            } catch (IllegalAccessException | InvocationTargetException e) {
               throw cannotCreate(keyEvent, e);
            }
         } else {
            invokeWithNoArgs(target, keyEvent, key);
         }
         ctx.consumePeeked(defEvent);
      } else if (defEvent instanceof MappingStartEvent) {
         Method method = findMethod(keyEvent, target, key, -1);
         Object[] args = new Object[method.getParameterCount()];
         Class<?>[] parameterTypes = method.getParameterTypes();
         for (int i = 0; i < parameterTypes.length; i++) {
            args[i] = defaultValue(parameterTypes[i]);
         }
         Object builder;
         try {
            builder = method.invoke(target, args);
         } catch (IllegalAccessException | InvocationTargetException e) {
            throw cannotCreate(keyEvent, e);
         }
         ctx.consumePeeked(defEvent);
         if (builder instanceof ServiceLoadedBuilder) {
            defEvent = ctx.next();
            if (defEvent instanceof MappingEndEvent) {
               throw new ParserException(defEvent, "Expecting builder type but found end of mapping.");
            } else if (defEvent instanceof ScalarEvent) {
               String name = ((ScalarEvent) defEvent).getValue();
               ServiceLoadedBuilder<?> serviceLoadedBuilder = (ServiceLoadedBuilder<?>) builder;
               LoadedBuilder loadedBuilder = serviceLoadedBuilder.forName(name);
               ctx.expectEvent(MappingStartEvent.class);
               applyMapping(ctx, loadedBuilder);
               // applyMapping consumes MappingEndEvent
            } else {
               throw ctx.unexpectedEvent(defEvent);
            }
            ctx.expectEvent(MappingEndEvent.class);
         } else {
            applyMapping(ctx, builder);
         }
      } else if (defEvent instanceof SequenceStartEvent) {
         Object builder;
         Method step = findMethod(keyEvent, target, key, 0);
         try {
            builder = step.invoke(target);
         } catch (IllegalAccessException | InvocationTargetException e) {
            throw cannotCreate(keyEvent, e);
         }
         if (builder instanceof BaseSequenceBuilder) {
            ctx.parseList((BaseSequenceBuilder) builder, StepParser.instance());
         } else {
            ctx.consumePeeked(defEvent);
            while (ctx.hasNext()) {
               defEvent = ctx.next();
               if (defEvent instanceof SequenceEndEvent) {
                  break;
               } else if (defEvent instanceof ScalarEvent) {
                  invokeWithParameters(ctx, builder, (ScalarEvent) defEvent);
               } else if (defEvent instanceof MappingStartEvent) {
                  defEvent = ctx.expectEvent(ScalarEvent.class);
                  invokeWithParameters(ctx, builder, (ScalarEvent) defEvent);
                  ctx.expectEvent(MappingEndEvent.class);
               } else {
                  throw ctx.unexpectedEvent(defEvent);
               }
            }
         }
      }
   }

   private void invokeWithNoArgs(Object target, ScalarEvent keyEvent, String key) throws ParserException {
      Method method = findMethod(keyEvent, target, key, 0);
      try {
         method.invoke(target);
      } catch (IllegalAccessException | InvocationTargetException e) {
         throw cannotCreate(keyEvent, e);
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

   private Method findMethod(Event event, Object target, String name, int params) throws ParserException {
      Method[] candidates = Stream.of(target.getClass().getDeclaredMethods()).filter(m -> m.getName().equals(name)).toArray(Method[]::new);
      if (candidates.length == 0) {
         throw new ParserException(event, "Cannot find method '" + name + "' on '" + target + "'");
      } else if (params >= 0) {
         return Stream.of(candidates).filter(m -> m.getParameterCount() == params).findAny()
               .orElseThrow(() -> new ParserException(event, "Wrong number of parameters to '" + name + "', expecting " + params));
      } else {
         return Stream.of(candidates).reduce(StepParser::selectMethod).get();
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

}
