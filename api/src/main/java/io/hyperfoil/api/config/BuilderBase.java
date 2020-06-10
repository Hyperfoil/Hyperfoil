package io.hyperfoil.api.config;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Intended base for all builders that might need relocation when the step is copied over.
 */
public interface BuilderBase<S extends BuilderBase<S>> {
   default void prepareBuild() {}

   /**
    * Some scenarios copy its parts from one place to another, either during parsing
    * phase (e.g. through YAML anchors) or in {@link #prepareBuild()}.
    * In order to make sure that modification in one place does not accidentally change
    * the original one we require defining a deep copy method on each builder. The only
    * exception is when the builder is immutable (including potential children builder);
    * in that case the deep copy is not necessary and this method can return <code>this</code>.
    * <p>
    * The default implementation uses reflection to create a deep copy of all collections and maps,
    * calling {@link #copy()} on all objects implementing {@link BuilderBase} and performing
    * a manual copy on those implementing {@link Rewritable}.
    *
    * @return Deep copy of this object.
    */
   @SuppressWarnings("unchecked")
   default S copy() {
      if (getClass().isSynthetic()) {
         // This is most likely a lambda supplier of the instance (which should be immutable anyway)
         assert getClass().getSimpleName().contains("$$Lambda$");
         return (S) this;
      }
      try {
         BuilderBase<?> copy = null;
         for (Constructor<?> ctor : getClass().getConstructors()) {
            if (ctor.getParameterCount() == 0) {
               copy = (BuilderBase<?>) ctor.newInstance();
               break;
            } else if (ctor.getParameterCount() == 1) {
               if (ctor.getParameterTypes()[0] == getClass()) {
                  // copy constructor
                  copy = (BuilderBase<?>) ctor.newInstance(this);
                  break;
               }
            }
         }
         if (copy == null) {
            throw new NoSuchMethodException("No constructor for " + getClass().getName());
         }
         Class<?> cls = getClass();
         while (cls != null && cls != BuilderBase.class) {
            for (Field f : cls.getDeclaredFields()) {
               f.setAccessible(true);
               if (Modifier.isStatic(f.getModifiers())) {
                  continue;
               } else if (Rewritable.class.isAssignableFrom(f.getType())) {
                  Object thisRewritable = f.get(this);
                  if (thisRewritable == null) {
                     continue;
                  }
                  Rewritable<Object> copyRewritable = (Rewritable<Object>) f.get(copy);
                  if (copyRewritable == null) {
                     copyRewritable = (Rewritable<Object>) thisRewritable.getClass().getConstructor().newInstance();
                     f.set(copy, copyRewritable);
                  }
                  copyRewritable.readFrom(thisRewritable);
               } else if (Modifier.isFinal(f.getModifiers())) {
                  Object thisValue = f.get(this);
                  Object copyValue = f.get(copy);
                  if (thisValue == copyValue) {
                     // usually happens when the value is null
                     continue;
                  } else if (copyValue instanceof Collection) {
                     // final collections can only get the elements
                     Collection<Object> copyCollection = (Collection<Object>) copyValue;
                     copyCollection.clear();
                     copyCollection.addAll((Collection<?>) CopyUtil.deepCopy(thisValue));
                  }
                  // This could be e.g. final list and we wouldn't copy it
                  throw new UnsupportedOperationException(cls.getName() + "." + f.getName() + " is final (actual instance: " + this + ")");
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
                  // use list in builders
                  throw new UnsupportedOperationException(cls.getName() + "." + f.getName() + " is an array (actual instance: " + this + ")");
               } else {
                  f.set(copy, CopyUtil.deepCopy(f.get(this)));
               }
            }
            cls = cls.getSuperclass();
         }
         return (S) copy;
      } catch (ReflectiveOperationException e) {
         throw new BenchmarkDefinitionException("Default deep copy failed", e);
      }
   }

   static <T extends BuilderBase<T>> List<T> copy(Collection<T> builders) {
      return builders.stream().map(b -> b.copy()).collect(Collectors.toList());
   }

   class CopyUtil {
      private static Object deepCopy(Object o) throws ReflectiveOperationException {
         if (o == null) {
            return null;
         } else if (BuilderBase.class.isAssignableFrom(o.getClass())) {
            return ((BuilderBase<?>) o).copy();
         } else if (Collection.class.isAssignableFrom(o.getClass())) {
            Collection<?> thisCollection = (Collection<?>) o;
            Collection<Object> newCollection = thisCollection.getClass().getConstructor().newInstance();
            for (Object item : thisCollection) {
               newCollection.add(deepCopy(item));
            }
            return newCollection;
         } else if (Map.class.isAssignableFrom(o.getClass())) {
            Map<?, ?> thisMap = (Map<?, ?>) o;
            Map<Object, Object> newMap = thisMap.getClass().getConstructor().newInstance();
            for (Map.Entry<?, ?> entry : thisMap.entrySet()) {
               newMap.put(deepCopy(entry.getKey()), deepCopy(entry.getValue()));
            }
            return newMap;
         } else {
            return o;
         }
      }
   }
}
