package io.hyperfoil.api.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Intended base for all builders that might need relocation when the step is copied over.
 */
public interface BuilderBase<S extends BuilderBase<S>> {
   default void prepareBuild() {
      Class<?> clz = getClass();
      while (clz != null && clz != Object.class) {
         for (Field f : clz.getDeclaredFields()) {
            if (f.isSynthetic() || Modifier.isStatic(f.getModifiers()) || "parent".equals(f.getName())) {
               continue;
            }
            f.setAccessible(true);
            try {
               tryPrepare(clz, f.getName(), f.getType(), f.get(this));
            } catch (IllegalAccessException e) {
               throw new UnsupportedOperationException("Cannot get value of " + clz.getName() + "." + f.getName() + " (actual instance: " + this + ")");
            }
         }
         clz = clz.getSuperclass();
      }
   }

   private void tryPrepare(Class<?> clz, String name, Class<?> type, Object value) throws IllegalAccessException {
      if (BuilderBase.class.isAssignableFrom(type)) {
         if (value != null) {
            ((BuilderBase<?>) value).prepareBuild();
         }
      } else if (Collection.class.isAssignableFrom(type)) {
         if (value != null) {
            for (Object item : (Collection<?>) value) {
               if (item != null) {
                  tryPrepare(clz, name, item.getClass(), item);
               }
            }
         }
      } else if (BaseSequenceBuilder.class.isAssignableFrom(type)) {
         if (value != null) {
            ((BaseSequenceBuilder<?>) value).prepareBuild();
         }
      } else if (Map.class.isAssignableFrom(type)) {
         if (value != null) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
               if (entry.getKey() != null) {
                  tryPrepare(clz, name, entry.getKey().getClass(), entry.getKey());
               }
               if (entry.getValue() != null) {
                  tryPrepare(clz, name, entry.getValue().getClass(), entry.getValue());
               }
            }
         }
      } else if (type.isArray()) {
         throw new UnsupportedOperationException(clz.getName() + "." + name + " is an array (actual instance: " + this + ")");
      }
   }

   /**
    * Some scenarios copy its parts from one place to another, either during parsing
    * phase (e.g. through YAML anchors) or in {@link #prepareBuild()}.
    * In order to make sure that modification in one place does not accidentally change
    * the original one we require defining a deep copy method on each builder. The only
    * exception is when the builder is immutable (including potential children builder);
    * in that case the deep copy is not necessary and this method can return <code>this</code>.
    * <p>
    * The default implementation uses reflection to create a deep copy of all collections and maps,
    * calling <code>copy()</code> on all objects implementing {@link BuilderBase}.
    *
    * @param newParent Object passed to a matching constructor.
    * @return Deep copy of this object.
    */
   @SuppressWarnings("unchecked")
   default S copy(Object newParent) {
      if (getClass().isSynthetic()) {
         // This is most likely a lambda supplier of the instance (which should be immutable anyway)
         assert getClass().getSimpleName().contains("$$Lambda$");
         return (S) this;
      }
      try {
         ThrowingSupplier<BuilderBase<?>> constructor = null;
         for (Constructor<?> ctor : getClass().getConstructors()) {
            // parameterless constructor has lowest priority
            if (ctor.getParameterCount() == 0 && constructor == null) {
               constructor = () -> (BuilderBase<?>) ctor.newInstance();
            } else if (ctor.getParameterCount() == 1) {
               Class<?> parameterType = ctor.getParameterTypes()[0];
               if (parameterType == getClass()) {
                  constructor = () -> (BuilderBase<?>) ctor.newInstance(this);
                  // copy constructor has highest priority
                  break;
               } else if (newParent != null && parameterType.isAssignableFrom(newParent.getClass())) {
                  constructor = () -> (BuilderBase<?>) ctor.newInstance(newParent);
               }
            }
         }
         if (constructor == null) {
            throw new NoSuchMethodException("No constructor for " + getClass().getName());
         }
         BuilderBase<?> copy = constructor.get();
         Class<?> cls = getClass();
         while (cls != null && cls != BuilderBase.class) {
            for (Field f : cls.getDeclaredFields()) {
               f.setAccessible(true);
               if (Modifier.isStatic(f.getModifiers())) {
                  // do not copy static fields
               } else if (f.isAnnotationPresent(IgnoreCopy.class)) {
                  // field is intentionally omitted
               } else if (Modifier.isFinal(f.getModifiers())) {
                  Object thisValue = f.get(this);
                  Object copyValue = f.get(copy);
                  if (thisValue == copyValue) {
                     // usually happens when the value is null
                  } else if (copyValue instanceof Collection) {
                     // final collections can only get the elements
                     Collection<Object> copyCollection = (Collection<Object>) copyValue;
                     copyCollection.clear();
                     copyCollection.addAll((Collection<?>) CopyUtil.deepCopy(thisValue, copy));
                  } else if (f.getName().equals("parent")) {
                     // Fluent builders often require parent element reference; in YAML configuration these are not used.
                  } else if (copyValue instanceof BaseSequenceBuilder && thisValue instanceof BaseSequenceBuilder) {
                     List<StepBuilder<?>> newSteps = ((BaseSequenceBuilder<?>) copyValue).steps;
                     ((BaseSequenceBuilder<?>) thisValue).steps.forEach(sb -> newSteps.add(sb.copy(copyValue)));
                  } else {
                     // This could be e.g. final list and we wouldn't copy it
                     throw new UnsupportedOperationException(cls.getName() + "." + f.getName() + " is final (actual instance: " + this + ")");
                  }
               } else if (f.getType().isPrimitive()) {
                  if (f.getType() == boolean.class) {
                     f.setBoolean(copy, f.getBoolean(this));
                  } else if (f.getType() == int.class) {
                     f.setInt(copy, f.getInt(this));
                  } else if (f.getType() == long.class) {
                     f.setLong(copy, f.getLong(this));
                  } else if (f.getType() == double.class) {
                     f.setDouble(copy, f.getDouble(this));
                  } else if (f.getType() == float.class) {
                     f.setFloat(copy, f.getFloat(this));
                  } else if (f.getType() == byte.class) {
                     f.setByte(copy, f.getByte(this));
                  } else if (f.getType() == char.class) {
                     f.setChar(copy, f.getChar(this));
                  } else if (f.getType() == short.class) {
                     f.setShort(copy, f.getShort(this));
                  } else {
                     throw new UnsupportedOperationException("Unknown primitive: " + f.getType());
                  }
               } else if (f.getType().isArray()) {
                  if (f.getType().getComponentType() == byte.class) {
                     byte[] bytes = (byte[]) f.get(this);
                     f.set(copy, bytes == null ? null : Arrays.copyOf(bytes, bytes.length));
                  } else {
                     // use list in builders
                     throw new UnsupportedOperationException(cls.getName() + "." + f.getName() + " is an array (actual instance: " + this + ")");
                  }
               } else {
                  f.set(copy, CopyUtil.deepCopy(f.get(this), copy));
               }
            }
            cls = cls.getSuperclass();
         }
         return (S) copy;
      } catch (ReflectiveOperationException e) {
         throw new BenchmarkDefinitionException("Default deep copy failed", e);
      }
   }

   interface ThrowingSupplier<T> {
      T get() throws ReflectiveOperationException;
   }

   class CopyUtil {
      private static Object deepCopy(Object o, Object newParent) throws ReflectiveOperationException {
         if (o == null) {
            return null;
         } else if (BuilderBase.class.isAssignableFrom(o.getClass())) {
            return ((BuilderBase<?>) o).copy(newParent);
         } else if (Collection.class.isAssignableFrom(o.getClass())) {
            Collection<?> thisCollection = (Collection<?>) o;
            @SuppressWarnings("unchecked")
            Collection<Object> newCollection = thisCollection.getClass().getConstructor().newInstance();
            for (Object item : thisCollection) {
               newCollection.add(deepCopy(item, newParent));
            }
            return newCollection;
         } else if (Map.class.isAssignableFrom(o.getClass())) {
            Map<?, ?> thisMap = (Map<?, ?>) o;
            @SuppressWarnings("unchecked")
            Map<Object, Object> newMap = thisMap.getClass().getConstructor().newInstance();
            for (Map.Entry<?, ?> entry : thisMap.entrySet()) {
               newMap.put(deepCopy(entry.getKey(), null), deepCopy(entry.getValue(), newParent));
            }
            return newMap;
         } else {
            return o;
         }
      }
   }

   /**
    * Used to ignore copying the field (e.g. when it's final, or we don't want to call it 'parent').
    */
   @Target(ElementType.FIELD)
   @Retention(RetentionPolicy.RUNTIME)
   @interface IgnoreCopy {
   }
}
