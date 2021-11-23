package io.hyperfoil.core.steps;

import java.lang.reflect.Array;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.BenchmarkExecutionException;
import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.BuilderBase;
import io.hyperfoil.api.config.InitFromParam;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.session.ObjectAccess;
import io.hyperfoil.api.session.Action;
import io.hyperfoil.api.session.ReadAccess;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.session.IntVar;
import io.hyperfoil.core.session.ObjectVar;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.function.SerializableConsumer;
import io.hyperfoil.function.SerializableFunction;

public class SetAction implements Action {
   private final ObjectAccess var;
   private final SerializableFunction<Session, Object> valueSupplier;

   public SetAction(ObjectAccess var, SerializableFunction<Session, Object> valueSupplier) {
      this.var = var;
      this.valueSupplier = valueSupplier;
   }

   @Override
   public void run(Session session) {
      var.setObject(session, valueSupplier.apply(session));
   }

   /**
    * Set variable in session to certain value.
    */
   @MetaInfServices(Action.Builder.class)
   @Name("set")
   public static class Builder implements InitFromParam<Builder>, Action.Builder {
      private String var;
      private Object value;
      private ObjectArrayBuilder objectArray;
      private IntArrayBuilder intArray;

      public Builder() {
      }

      /**
       * @param param Use <code>var &lt;- value</code>.
       * @return Self.
       */
      @Override
      public Builder init(String param) {
         int sep = param.indexOf("<-");
         if (sep < 0) {
            throw new BenchmarkDefinitionException("Invalid inline definition '" + param + "': should be 'var <- value'");
         }
         this.var = param.substring(0, sep).trim();
         this.value = param.substring(sep + 2).trim();
         return this;
      }

      /**
       * Variable name.
       *
       * @param var Variable name.
       * @return Self.
       */
      public Builder var(String var) {
         this.var = var;
         return this;
      }

      /**
       * String value.
       *
       * @param value Value.
       * @return Self.
       */
      public Builder value(String value) {
         this.value = value;
         return this;
      }

      /**
       * Set variable to an (unset) object array.
       *
       * @return Builder.
       */
      public ObjectArrayBuilder objectArray() {
         return objectArray = new ObjectArrayBuilder(this);
      }

      /**
       * Set variable to an (unset) integer array.
       *
       * @return Builder.
       */
      public IntArrayBuilder intArray() {
         return intArray = new IntArrayBuilder(this);
      }

      @Override
      public SetAction build() {
         if (var == null) {
            throw new BenchmarkDefinitionException("Variable name was not set!");
         }
         if (Stream.of(value, objectArray, intArray).filter(Objects::nonNull).count() != 1) {
            throw new BenchmarkDefinitionException("Must set exactly on of: value, objectArray, intArray");
         }
         if (value != null) {
            Object myValue = value;
            return new SetAction(SessionFactory.objectAccess(var), new ConstantValue(myValue));
         } else if (objectArray != null) {
            return new SetAction(SessionFactory.objectAccess(var), objectArray.build());
         } else {
            return new SetAction(SessionFactory.objectAccess(var), intArray.build());
         }
      }

      private static class ConstantValue implements SerializableFunction<Session, Object> {
         private final Object value;

         public ConstantValue(Object value) {
            this.value = value;
         }

         @Override
         public Object apply(Session s) {
            return value;
         }
      }
   }

   private static class ValueResource<T> implements Session.Resource {
      private final T object;
      private final SerializableConsumer<T> reset;

      public ValueResource(T o, SerializableConsumer<T> reset) {
         this.object = o;
         this.reset = reset;
      }

      @Override
      public void onSessionReset(Session session) {
         reset.accept(object);
      }
   }

   private abstract static class ValueSupplier<T> implements SerializableFunction<Session, Object>, Session.ResourceKey<ValueResource<T>>, ResourceUtilizer {
      @Override
      public T apply(Session session) {
         return session.getResource(this).object;
      }

      @Override
      public void reserve(Session session) {
         session.declareResource(this, () -> new ValueResource<>(create(session), this::reset));
      }

      protected abstract T create(Session session);

      protected abstract void reset(T object);
   }

   public abstract static class BaseArrayBuilder<S extends BaseArrayBuilder<S>> implements BuilderBase<S> {
      protected final Builder parent;
      protected int size;
      protected String fromVar;

      BaseArrayBuilder(Builder parent) {
         this.parent = parent;
      }

      /**
       * Size of the array.
       *
       * @param size Array size.
       * @return Self.
       */
      public S size(int size) {
         this.size = size;
         return self();
      }

      /**
       * Contents of the new array. If the variable contains an array or a list, items will be copied
       * to the elements with the same index up to the size of this array.
       * If the variable contains a different value all elements will be initialized to this value.
       *
       * @param fromVar Variable name.
       * @return Self.
       */
      public S fromVar(String fromVar) {
         this.fromVar = fromVar;
         return self();
      }

      @SuppressWarnings("unchecked")
      protected S self() {
         return (S) this;
      }

      public Builder end() {
         return parent;
      }

      protected int ensureNonNegativeSize() {
         if (size < 0) {
            throw new BenchmarkDefinitionException("Size must not be negative!");
         }
         return size;
      }

      protected static void resetArray(Session.Var[] array) {
         for (int i = 0; i < array.length; ++i) {
            array[i].unset();
         }
      }
   }

   /**
    * Creates object arrays to be stored in the session.
    */
   public static class ObjectArrayBuilder extends BaseArrayBuilder<ObjectArrayBuilder> {
      public ObjectArrayBuilder(Builder parent) {
         super(parent);
      }

      private ValueSupplier<ObjectVar[]> build() {
         int mySize = ensureNonNegativeSize();
         return new ObjectArraySupplier(mySize, SessionFactory.readAccess(fromVar));
      }
   }

   private static class ObjectArraySupplier extends ValueSupplier<ObjectVar[]> {
      private final int size;
      private final ReadAccess fromVar;

      public ObjectArraySupplier(int size, ReadAccess fromVar) {
         this.size = size;
         this.fromVar = fromVar;
      }

      @Override
      protected ObjectVar[] create(Session session) {
         return ObjectVar.newArray(session, size);
      }

      @Override
      public ObjectVar[] apply(Session session) {
         ObjectVar[] newArray = super.apply(session);
         if (fromVar != null) {
            Object value = fromVar.getObject(session);
            if (value == null) {
               // ignore
            } else if (value instanceof ObjectVar[]) {
               ObjectVar[] vars = (ObjectVar[]) value;
               for (int i = 0; i < Math.min(size, vars.length); ++i) {
                  if (vars[i].isSet()) {
                     newArray[i].set(vars[i].objectValue(session));
                  }
               }
            } else if (value instanceof IntVar[]) {
               session.fail(new BenchmarkExecutionException("Type mismatch - are you trying to copy integers into objects?"));
            } else if (value.getClass().isArray()) {
               int length = Math.min(size, Array.getLength(value));
               for (int i = 0; i < length; ++i) {
                  newArray[i].set(Array.get(value, i));
               }
            } else if (value instanceof List) {
               List<?> list = (List<?>) value;
               int length = Math.min(size, list.size());
               for (int i = 0; i < length; ++i) {
                  newArray[i].set(list.get(i));
               }
            } else {
               for (int i = 0; i < size; ++i) {
                  newArray[i].set(value);
               }
            }
         }
         return newArray;
      }

      @Override
      protected void reset(ObjectVar[] object) {
         BaseArrayBuilder.resetArray(object);
      }
   }

   /**
    * Creates integer arrays to be stored in the session.
    */
   public static class IntArrayBuilder extends BaseArrayBuilder<IntArrayBuilder> {
      public IntArrayBuilder(Builder parent) {
         super(parent);
      }

      private ValueSupplier<IntVar[]> build() {
         // prevent capturing this object reference in the lambda
         int mySize = ensureNonNegativeSize();
         return new IntArraySupplier(mySize, SessionFactory.readAccess(fromVar));
      }

   }

   private static class IntArraySupplier extends ValueSupplier<IntVar[]> {
      private final int size;
      private final ReadAccess fromVar;

      public IntArraySupplier(int size, ReadAccess fromVar) {
         this.size = size;
         this.fromVar = fromVar;
      }

      @Override
      protected IntVar[] create(Session session) {
         return IntVar.newArray(session, size);
      }

      @Override
      public IntVar[] apply(Session session) {
         IntVar[] newArray = super.apply(session);
         if (fromVar != null) {
            Session.Var var = fromVar.getVar(session);
            if (var.type() == Session.VarType.OBJECT) {
               Object value = var.objectValue(session);
               if (value == null) {
                  // ignore
               } else if (value instanceof ObjectVar[]) {
                  session.fail(new BenchmarkExecutionException("Type mismatch - are you trying to copy integers into objects?"));
               } else if (value instanceof IntVar[]) {
                  IntVar[] vars = (IntVar[]) value;
                  for (int i = 0; i < Math.min(size, vars.length); ++i) {
                     if (vars[i].isSet()) {
                        newArray[i].set(vars[i].intValue(session));
                     }
                  }
               } else if (value.getClass().isArray()) {
                  int length = Math.min(size, Array.getLength(value));
                  for (int i = 0; i < length; ++i) {
                     newArray[i].set(Array.getInt(value, i));
                  }
               } else if (value instanceof List) {
                  List<?> list = (List<?>) value;
                  int length = Math.min(size, list.size());
                  for (int i = 0; i < length; ++i) {
                     newArray[i].set((int) list.get(i));
                  }
               } else {
                  session.fail(new IllegalArgumentException("Cannot use " + value + " to initialize an integer array"));
               }
            } else {
               int value = var.intValue(session);
               for (int i = 0; i < size; ++i) {
                  newArray[i].set(value);
               }
            }
         }
         return newArray;
      }

      @Override
      protected void reset(IntVar[] object) {
         // TODO: Customise this generated block
      }
   }
}
