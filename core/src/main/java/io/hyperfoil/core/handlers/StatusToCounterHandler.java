package io.hyperfoil.core.handlers;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.Locator;
import io.hyperfoil.api.connection.Request;
import io.hyperfoil.api.http.StatusHandler;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.session.ResourceUtilizer;

public class StatusToCounterHandler implements StatusHandler, ResourceUtilizer {
   private final Integer expectStatus;
   private final String var;
   private final int init;
   private final Integer add;
   private final Integer set;

   public StatusToCounterHandler(int expectStatus, String var, int init, Integer add, Integer set) {
      this.expectStatus = expectStatus;
      this.var = var;
      this.init = init;
      this.add = add;
      this.set = set;
   }

   @Override
   public void handleStatus(Request request, int status) {
      if (expectStatus != null && expectStatus != status) {
         return;
      }
      if (add != null) {
         if (request.session.isSet(var)) {
            request.session.addToInt(var, add);
         } else {
            request.session.setInt(var, init + add);
         }
      } else if (set != null) {
         request.session.setInt(var, set);
      } else {
         throw new IllegalStateException();
      }
   }

   @Override
   public void reserve(Session session) {
      session.declareInt(var);
   }

   public static class Builder implements StatusHandler.Builder {
      private Integer expectStatus;
      private String var;
      private int init;
      private Integer add;
      private Integer set;

      public Builder expectStatus(int expectStatus) {
         this.expectStatus = expectStatus;
         return this;
      }

      public Builder var(String var) {
         this.var = var;
         return this;
      }

      public Builder init(int init) {
         this.init = init;
         return this;
      }

      public Builder add(int add) {
         this.add = add;
         return this;
      }

      public Builder set(int set) {
         this.set = set;
         return this;
      }

      @Override
      public StatusHandler build() {
         if (add != null && set != null) {
            throw new BenchmarkDefinitionException("Use either 'add' or 'set' (not both)");
         } else if (add == null && set == null) {
            throw new BenchmarkDefinitionException("Use either 'add' or 'set'");
         }
         return new StatusToCounterHandler(expectStatus, var, init, add, set);
      }
   }

   @MetaInfServices(StatusHandler.BuilderFactory.class)
   public static class BuilderFactory implements StatusHandler.BuilderFactory {
      @Override
      public String name() {
         return "counter";
      }

      @Override
      public boolean acceptsParam() {
         return false;
      }

      @Override
      public Builder newBuilder(Locator locator, String param) {
         return new Builder();
      }
   }
}
