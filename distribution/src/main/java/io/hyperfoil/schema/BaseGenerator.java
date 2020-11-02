package io.hyperfoil.schema;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import io.hyperfoil.api.config.BaseSequenceBuilder;
import io.hyperfoil.api.config.Embed;
import io.hyperfoil.api.config.InitFromParam;
import io.hyperfoil.api.config.ListBuilder;
import io.hyperfoil.api.config.MappingListBuilder;
import io.hyperfoil.api.config.PairBuilder;
import io.hyperfoil.api.config.PartialBuilder;

class BaseGenerator {
   private static final Pattern END_REGEXP = Pattern.compile("^end(\\p{javaUpperCase}.*|$)");

   static Class<?> getRawClass(java.lang.reflect.Type type) {
      if (type instanceof Class) {
         return (Class<?>) type;
      } else if (type instanceof ParameterizedType) {
         return (Class<?>) ((ParameterizedType) type).getRawType();
      } else {
         throw new IllegalStateException("Cannot analyze type " + type);
      }
   }

   static boolean isMethodIgnored(Class<?> builder, Method m) {
      if (Modifier.isStatic(m.getModifiers()) || m.isDefault() || m.isSynthetic() || m.isBridge()) {
         return true;
      } else if (END_REGEXP.matcher(m.getName()).matches()) {
         return true;
      } else if (m.getParameterCount() > 1) {
         return true;
      } else if (m.getParameterCount() == 1 && !isParamConvertible(m.getParameters()[0].getType())) {
         return true;
      } else if (PairBuilder.class.isAssignableFrom(builder) && m.getName().equals("accept") && m.getParameterCount() == 2) {
         return true;
      } else if (PartialBuilder.class.isAssignableFrom(builder) && m.getName().equals("withKey") && m.getParameterCount() == 1) {
         return true;
      } else if (ListBuilder.class.isAssignableFrom(builder) && m.getName().equals("nextItem") && m.getParameterCount() == 1) {
         return true;
      } else if (MappingListBuilder.class.isAssignableFrom(builder) && m.getName().equals("addItem") && m.getParameterCount() == 0) {
         return true;
      } else if (m.getName().equals("init") && m.getParameterCount() == 1 && m.getParameterTypes()[0] == String.class && InitFromParam.class.isAssignableFrom(builder)) {
         return true;
      } else if (m.getName().equals("copy") && m.getParameterCount() == 0) {
         return true;
      } else if (m.getAnnotation(Deprecated.class) != null) {
         return true;
      } else if (m.getName().equals("rootSequence") && BaseSequenceBuilder.class.isAssignableFrom(m.getDeclaringClass())) {
         return true;
      }
      return false;
   }

   private static boolean isParamConvertible(Class<?> type) {
      return type == String.class || type == CharSequence.class || type.isPrimitive() || type.isEnum();
   }

   protected static void findProperties(Class<?> root, Consumer<Method> processProperty) {
      Queue<Class<?>> todo = new ArrayDeque<>();
      Set<Class<?>> visited = new HashSet<>();
      todo.add(root);
      while (!todo.isEmpty()) {
         Class<?> b = todo.poll();
         visited.add(b);
         for (Method m : b.getMethods()) {
            if (isMethodIgnored(b, m)) {
               continue;
            }
            processProperty.accept(m);
         }
         for (Field f : b.getFields()) {
            if (f.isAnnotationPresent(Embed.class)) {
               if (!visited.contains(f.getType()) && !Modifier.isStatic(f.getModifiers())) {
                  todo.add(f.getType());
               }
            }
         }
         for (Method m : b.getMethods()) {
            if (m.isAnnotationPresent(Embed.class)) {
               if (!visited.contains(m.getReturnType()) && m.getParameterCount() == 0 && !Modifier.isStatic(m.getModifiers())) {
                  todo.add(m.getReturnType());
               }
            }
         }
      }
   }
}
