package io.sailrocket.core.parser;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
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
   private static final Map<String, Method> STEPS = Stream.of(StepDiscriminator.class.getDeclaredMethods())
         .collect(Collectors.toMap(Method::getName, Function.identity(), StepParser::selectMethod));
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
      if (firstEvent instanceof ScalarEvent) {
         ScalarEvent stepEvent = (ScalarEvent) firstEvent;
         Method step = STEPS.get(stepEvent.getValue());
         if (step == null) {
            throw new ParserException(stepEvent, "Unknown step '" + stepEvent.getValue() + "'");
         }
         try {
            step.invoke(target.step());
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
      Method step = STEPS.get(stepEvent.getValue());
      if (step == null) {
         throw new ParserException(stepEvent, "Unknown step '" + stepEvent.getValue() + "'");
      }
      if (!ctx.hasNext()) {
         throw ctx.noMoreEvents(ScalarEvent.class, MappingStartEvent.class, MappingEndEvent.class, SequenceStartEvent.class);
      }
      Event defEvent = ctx.peek();
      if (defEvent instanceof MappingEndEvent) {
         // end of list item -> no arg step
         try {
            step.invoke(target.step());
         } catch (IllegalAccessException | InvocationTargetException e) {
            throw cannotCreate(stepEvent, e);
         }
         // we'll expect the mapping end at the end
      } else if (defEvent instanceof ScalarEvent) {
         // - step : param syntax
         String value = ((ScalarEvent) defEvent).getValue();
         if (step.getParameterCount() != 1) {
            throw new ParserException(defEvent, "Step '" + stepEvent.getValue() + "' does not have single parameter.");
         }
         Object param = convert(defEvent, value, step.getParameterTypes()[0]);
         try {
            step.invoke(target.step(), param);
         } catch (IllegalAccessException | InvocationTargetException e) {
            throw cannotCreate(stepEvent, e);
         }
         ctx.consumePeeked(defEvent);
      } else if (defEvent instanceof MappingStartEvent) {
         Object[] args = new Object[step.getParameterCount()];
         Class<?>[] parameterTypes = step.getParameterTypes();
         for (int i = 0; i < parameterTypes.length; i++) {
            args[i] = defaultValue(parameterTypes[i]);
         }
         Object builder;
         try {
            builder = step.invoke(target.step(), args);
         } catch (IllegalAccessException | InvocationTargetException e) {
            throw cannotCreate(stepEvent, e);
         }
         ctx.consumePeeked(defEvent);
         while (ctx.hasNext()) {
            defEvent = ctx.next();
            if (defEvent instanceof MappingEndEvent) {
               break;
            } else if (defEvent instanceof ScalarEvent) {
               Method setter = findMethod(defEvent, builder, ((ScalarEvent) defEvent).getValue());
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
         try {
            builder = step.invoke(target.step());
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

   private Method findMethod(Event event, Object target, String setter) throws ParserException {
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
