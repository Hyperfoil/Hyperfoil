package io.hyperfoil.core.parser;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Function;

import io.hyperfoil.api.config.BenchmarkDefinitionException;

class ReflectionParser<T, S> extends AbstractParser<T, S> {
   private final Function<T, S> selector;

   ReflectionParser(Function<T, S> selector, Class<S> builderClazz) {
      this.selector = selector;
      for (Method m : builderClazz.getMethods()) {
         if (m.getParameterCount() != 1 || m.getReturnType() != builderClazz) {
            continue;
         }
         Class<?> param = m.getParameterTypes()[0];
         if (param == String.class) {
            register(m.getName(), new PropertyParser.String<>((builder, value) -> invoke(m, builder, value)));
         } else if (param == Boolean.class || param == boolean.class) {
            register(m.getName(), new PropertyParser.Boolean<>((builder, value) -> invoke(m, builder, value)));
         } else if (param == Integer.class || param == int.class) {
            register(m.getName(), new PropertyParser.Int<>((builder, value) -> invoke(m, builder, value)));
         } else if (param == Double.class || param == double.class) {
            register(m.getName(), new PropertyParser.Double<>((builder, value) -> invoke(m, builder, value)));
         } else if (param == Long.class || param == long.class) {
            register(m.getName(), new PropertyParser.Long<>((builder, value) -> invoke(m, builder, value)));
         }
      }
   }

   private void invoke(Method m, S builder, Object value) {
      try {
         m.invoke(builder, value);
      } catch (IllegalAccessException | InvocationTargetException e) {
         throw new BenchmarkDefinitionException("Cannot set property " + m.getName() + " on " + builder.getClass().getSimpleName(), e);
      }
   }

   @Override
   public void parse(Context ctx, T target) throws ParserException {
      callSubBuilders(ctx, selector.apply(target));
   }
}
