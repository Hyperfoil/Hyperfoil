package io.hyperfoil.impl;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import io.hyperfoil.api.config.Visitor;
import io.hyperfoil.api.session.ReadAccess;

public class ReflectionAcceptor {
   private static final Class<?>[] BOXING_TYPES = new Class[]{
         Boolean.class, Integer.class, Long.class, Double.class, Byte.class, Character.class, Short.class, Float.class, Void.class
   };

   public static int accept(Object target, Visitor visitor) {
      int written = 0;
      for (Field f : getAllFields(target)) {
         if (f.getDeclaringClass().getModule() != ReflectionAcceptor.class.getModule()) {
            // we have wandered astray
            continue;
         }
         if (f.trySetAccessible()) {
            try {
               Visitor.Invoke invoke = f.getAnnotation(Visitor.Invoke.class);
               if (invoke == null) {
                  Object value = f.get(target);
                  if (visitor.visit(f.getName(), value, f.getGenericType())) {
                     written++;
                  }
               } else {
                  Method method = f.getDeclaringClass().getMethod(invoke.method());
                  method.setAccessible(true);
                  Object value = method.invoke(target);
                  if (visitor.visit(f.getName(), value, method.getGenericReturnType())) {
                     written++;
                  }
               }
            } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
               throw new RuntimeException(e);
            }
         }
      }
      return written;
   }

   private static List<Field> getAllFields(Object target) {
      Class<?> cls = target.getClass();
      List<Field> fields = new ArrayList<>();
      while (cls != null && cls != Object.class) {
         for (Field f : cls.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || f.isSynthetic() || f.isAnnotationPresent(Visitor.Ignore.class)) {
               // ignored fields
            } else if ("parent".equals(f.getName())) {
               // ignored as well
            } else {
               fields.add(f);
            }
         }
         cls = cls.getSuperclass();
      }
      fields.sort(Comparator.comparing(Field::getName));
      return fields;
   }

   public static boolean isScalar(Object value) {
      if (value == null) {
         return true;
      }
      if (value instanceof CharSequence || value instanceof ReadAccess) {
         return true;
      }
      Class<?> cls = value.getClass();
      // the superclass.isEnum is needed for enums with extra methods - these become non-enum somehow
      return cls.isPrimitive() ||
            cls.isEnum() || (cls.getSuperclass() != null && cls.getSuperclass().isEnum()) ||
            Stream.of(BOXING_TYPES).anyMatch(c -> c == cls);
   }
}
