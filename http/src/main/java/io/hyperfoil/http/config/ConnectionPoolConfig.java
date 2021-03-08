package io.hyperfoil.http.config;

import java.io.Serializable;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.Rewritable;

public class ConnectionPoolConfig implements Serializable {
   private final int core;
   private final int max;
   private final int buffer;
   private final long keepAliveTime;

   public ConnectionPoolConfig(int core, int max, int buffer, long keepAliveTime) {
      this.core = core;
      this.max = max;
      this.buffer = buffer;
      this.keepAliveTime = keepAliveTime;
   }

   public int core() {
      return core;
   }

   public int max() {
      return max;
   }

   public int buffer() {
      return buffer;
   }

   public long keepAliveTime() {
      return keepAliveTime;
   }

   public static class Builder implements Rewritable<Builder> {
      private final HttpBuilder parent;
      private int core;
      private int max;
      private int buffer;
      private long keepAliveTime;

      public Builder(HttpBuilder parent) {
         this.parent = parent;
      }

      public Builder core(int core) {
         this.core = core;
         return this;
      }

      public Builder max(int max) {
         this.max = max;
         return this;
      }

      public Builder buffer(int buffer) {
         this.buffer = buffer;
         return this;
      }

      public Builder keepAliveTime(long keepAliveTime) {
         this.keepAliveTime = keepAliveTime;
         return this;
      }

      @Override
      public void readFrom(Builder other) {
         this.core = other.core;
         this.max = other.max;
         this.buffer = other.buffer;
         this.keepAliveTime = other.keepAliveTime;
      }

      public ConnectionPoolConfig build() {
         if (core < 0) {
            throw new BenchmarkDefinitionException("Illegal value for 'core': " + core + " (must be >= 0)");
         } else if (max < 0) {
            throw new BenchmarkDefinitionException("Illegal value for 'max': " + max + " (must be >= 0)");
         } else if (buffer < 0) {
            throw new BenchmarkDefinitionException("Illegal value for 'buffer': " + buffer + " (must be >= 0)");
         }
         if (core > max) {
            throw new BenchmarkDefinitionException("'core' > 'max': " + core + " > " + max);
         } else if (buffer > max) {
            throw new BenchmarkDefinitionException("'buffer' > 'max': " + buffer + " > " + max);
         }
         return new io.hyperfoil.http.config.ConnectionPoolConfig(core, max, buffer, keepAliveTime);
      }

      public HttpBuilder end() {
         return parent;
      }
   }
}
