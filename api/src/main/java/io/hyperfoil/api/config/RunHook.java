package io.hyperfoil.api.config;

import java.io.Serializable;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public abstract class RunHook implements Serializable, Comparable<RunHook> {
   protected final String name;

   protected RunHook(String name) {
      this.name = Objects.requireNonNull(name);
   }

   public String name() {
      return name;
   }

   public abstract boolean run(Map<String, String> properties, Consumer<String> outputConsumer);

   @Override
   public int compareTo(RunHook other) {
      return name.compareTo(other.name);
   }

   public interface Builder {
      RunHook build(String name);
   }
}
