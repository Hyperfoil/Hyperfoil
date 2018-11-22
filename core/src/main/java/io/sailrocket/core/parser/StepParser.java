package io.sailrocket.core.parser;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.stream.Stream;

import org.yaml.snakeyaml.events.Event;
import org.yaml.snakeyaml.events.MappingEndEvent;
import org.yaml.snakeyaml.events.MappingStartEvent;
import org.yaml.snakeyaml.events.ScalarEvent;
import org.yaml.snakeyaml.events.SequenceStartEvent;

import io.sailrocket.core.builders.BaseSequenceBuilder;
import io.sailrocket.core.builders.SequenceBuilder;
import io.sailrocket.core.builders.StepDiscriminator;

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

   @Override
   public void parse(Context ctx, BaseSequenceBuilder target) throws ParserException {
      Event firstEvent = ctx.next();
      StepDiscriminator discriminator = target.step();
      if (firstEvent instanceof ScalarEvent) {
         ScalarEvent stepEvent = (ScalarEvent) firstEvent;
         Method step = findMethod(stepEvent, discriminator, stepEvent.getValue(), 0);
         try {
            step.invoke(discriminator);
         } catch (IllegalAccessException | InvocationTargetException e) {
            throw cannotCreate(stepEvent, e);
         }
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
      Event defEvent = ctx.peek();
      if (defEvent instanceof MappingEndEvent) {
         // end of list item -> no arg step
         Method step = findMethod(stepEvent, discriminator, stepEvent.getValue(), 0);
         try {
            step.invoke(discriminator);
         } catch (IllegalAccessException | InvocationTargetException e) {
            throw cannotCreate(stepEvent, e);
         }
         // we'll expect the mapping end at the end
      } else if (defEvent instanceof ScalarEvent) {
         // - step : param syntax
         String value = ((ScalarEvent) defEvent).getValue();
         Method step = findMethod(stepEvent, discriminator, stepEvent.getValue(), 1);
         Object param = convert(defEvent, value, step.getParameterTypes()[0]);
         try {
            step.invoke(discriminator, param);
         } catch (IllegalAccessException | InvocationTargetException e) {
            throw cannotCreate(stepEvent, e);
         }
         ctx.consumePeeked(defEvent);
      } else if (defEvent instanceof MappingStartEvent) {
         Method step = findMethod(stepEvent, discriminator, stepEvent.getValue(), -1);
         Object[] args = new Object[step.getParameterCount()];
         Class<?>[] parameterTypes = step.getParameterTypes();
         for (int i = 0; i < parameterTypes.length; i++) {
            args[i] = defaultValue(parameterTypes[i]);
         }
         Object builder;
         try {
            builder = step.invoke(discriminator, args);
         } catch (IllegalAccessException | InvocationTargetException e) {
            throw cannotCreate(stepEvent, e);
         }
         ctx.consumePeeked(defEvent);
         while (ctx.hasNext()) {
            defEvent = ctx.next();
            if (defEvent instanceof MappingEndEvent) {
               break;
            } else if (defEvent instanceof ScalarEvent) {
               Method setter = findSetter(defEvent, builder, ((ScalarEvent) defEvent).getValue());
               Object[] setterArgs;
               ScalarEvent paramEvent = ctx.expectEvent(ScalarEvent.class);
               if (setter.getParameterCount() == 0) {
                  if (paramEvent.getValue() != null && !paramEvent.getValue().isEmpty()) {
                     throw new ParserException(paramEvent, "Setter '" + setter.getName() + "' has no args, keep the mapping empty.");
                  }
                  setterArgs = new Object[0];
               } else {
                  setterArgs = new Object[] { convert(defEvent, paramEvent.getValue(), setter.getParameterTypes()[0]) };
               }
               try {
                  setter.invoke(builder, setterArgs);
               } catch (IllegalAccessException | InvocationTargetException e) {
                  throw new ParserException(defEvent, "Cannot run setter '" + setter + "'", e);
               }
            } else {
               throw ctx.unexpectedEvent(defEvent);
            }
         }
      } else if (defEvent instanceof SequenceStartEvent) {
         Object builder;
         Method step = findMethod(stepEvent, discriminator, stepEvent.getValue(), 0);
         try {
            builder = step.invoke(discriminator);
         } catch (IllegalAccessException | InvocationTargetException e) {
            throw cannotCreate(stepEvent, e);
         }
         if (!(builder instanceof BaseSequenceBuilder)) {
            throw new ParserException(defEvent, "Builder on step '" + stepEvent.getValue() + "' does not allow nested steps.");
         }
         ctx.parseList((BaseSequenceBuilder) builder, StepParser.instance());
      }
      ctx.expectEvent(MappingEndEvent.class);
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

   private Method findSetter(Event event, Object target, String setter) throws ParserException {
      return Stream.of(target.getClass().getMethods())
            .filter(m -> setter.equals(m.getName()) && m.getParameterCount() <= 1).findFirst()
            .orElseThrow(() -> new ParserException(event, "Cannot find setter '" + setter + "' on '" + target + "'"));
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
      return new ParserException(event, "Cannot create step '" + event.getValue() + "'", exception);
   }

}
