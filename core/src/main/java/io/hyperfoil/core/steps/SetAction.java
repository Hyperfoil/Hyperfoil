package io.hyperfoil.core.steps;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.InitFromParam;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.Action;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.session.ObjectVar;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.function.SerializableConsumer;
import io.hyperfoil.function.SerializableFunction;

public class SetAction implements Action, ResourceUtilizer {
   private final Access var;
   private final SerializableFunction<Session, Object> valueSupplier;

   public SetAction(String var, SerializableFunction<Session, Object> valueSupplier) {
      this.var = SessionFactory.access(var);
      this.valueSupplier = valueSupplier;
   }

   @Override
   public void run(Session session) {
      var.setObject(session, valueSupplier.apply(session));
   }

   @Override
   public void reserve(Session session) {
      var.declareObject(session);
      ResourceUtilizer.reserve(session, valueSupplier);
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
         Object value = param.substring(sep + 2).trim();
         this.value = value;
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
       * Set variable to an (empty) array.
       *
       * @return Builder.
       */
      public ObjectArrayBuilder objectArray() {
         return objectArray = new ObjectArrayBuilder();
      }

      @Override
      public SetAction build() {
         if (var == null) {
            throw new BenchmarkDefinitionException("Variable name was not set!");
         }
         if (value == null && objectArray == null || value != null && objectArray != null) {
            throw new BenchmarkDefinitionException("Must set exactly on of: value, objectArray");
         }
         if (value != null) {
            Object myValue = value;
            return new SetAction(var, s -> myValue);
         } else {
            return new SetAction(var, objectArray.build());
         }
      }
   }

   private static class ValueResource<T> implements Session.Resource {
      private T object;

      public ValueResource(T o) {
         this.object = o;
      }
   }

   private static class ValueSupplier<T> implements SerializableFunction<Session, Object>, Session.ResourceKey<ValueResource<T>>, ResourceUtilizer {
      private final SerializableFunction<Session, T> supplier;
      private final SerializableConsumer<T> reset;

      private ValueSupplier(SerializableFunction<Session, T> supplier, SerializableConsumer<T> reset) {
         this.supplier = supplier;
         this.reset = reset;
      }

      @Override
      public Object apply(Session session) {
         T object = session.getResource(this).object;
         reset.accept(object);
         return object;
      }

      @Override
      public void reserve(Session session) {
         session.declareResource(this, new ValueResource<>(supplier.apply(session)));
      }
   }

   /**
    * Creates object arrays to be stored in the session.
    */
   public static class ObjectArrayBuilder {
      private int size;

      /**
       * Size of the array.
       *
       * @param size Array size.
       * @return Self.
       */
      public ObjectArrayBuilder size(int size) {
         this.size = size;
         return this;
      }

      private ValueSupplier<ObjectVar[]> build() {
         if (size <= 0) {
            throw new BenchmarkDefinitionException("Size must be positive!");
         }
         // prevent capturing this object reference in the lambda
         int mySize = size;
         return new ValueSupplier<>(session -> ObjectVar.newArray(session, mySize), array -> {
            for (int i = 0; i < array.length; ++i) {
               array[i].unset();
            }
            ;
         });
      }
   }
}
