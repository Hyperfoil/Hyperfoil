package io.hyperfoil.core.handlers;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.connection.Request;
import io.hyperfoil.api.http.StatusHandler;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.core.session.SessionFactory;

public class StatusToCounterHandler implements StatusHandler, ResourceUtilizer {
   private final Integer expectStatus;
   private final Access var;
   private final int init;
   private final Integer add;
   private final Integer set;

   public StatusToCounterHandler(int expectStatus, Access var, int init, Integer add, Integer set) {
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
         if (var.isSet(request.session)) {
            var.addToInt(request.session, add);
         } else {
            var.setInt(request.session, init + add);
         }
      } else if (set != null) {
         var.setInt(request.session, set);
      } else {
         throw new IllegalStateException();
      }
   }

   @Override
   public void reserve(Session session) {
      var.declareInt(session);
   }

   /**
    * Counts how many times given status is received.
    */
   @MetaInfServices(StatusHandler.Builder.class)
   @Name("counter")
   public static class Builder implements StatusHandler.Builder {
      private Integer expectStatus;
      private String var;
      private int init;
      private Integer add;
      private Integer set;

      /**
       * Expected status (others are ignored). All status codes match by default.
       *
       * @param expectStatus Status code.
       * @return Self.
       */
      public Builder expectStatus(int expectStatus) {
         this.expectStatus = expectStatus;
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
       * Initial value for the session variable.
       *
       * @param init Initial value.
       * @return Self.
       */
      public Builder init(int init) {
         this.init = init;
         return this;
      }

      /**
       * Number to be added to the session variable.
       *
       * @param add Value.
       * @return Self.
       */
      public Builder add(int add) {
         this.add = add;
         return this;
      }

      /**
       * Do not accumulate (add), just set the variable to this value.
       *
       * @param set Value.
       * @return Self.
       */
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
         return new StatusToCounterHandler(expectStatus, SessionFactory.access(var), init, add, set);
      }
   }
}
