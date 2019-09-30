package io.hyperfoil.core.builders;

import java.util.function.Function;

public class BuilderInfo<B> {
   public final Class<?> implClazz;
   public final Function<Object, B> adapter;

   public BuilderInfo(Class<?> implClazz, Function<Object, B> adapter) {
      this.implClazz = implClazz;
      this.adapter = adapter;
   }
}
