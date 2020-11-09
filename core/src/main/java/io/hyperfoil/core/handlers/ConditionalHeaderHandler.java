package io.hyperfoil.core.handlers;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.Embed;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.connection.HttpRequest;
import io.hyperfoil.api.http.HeaderHandler;
import io.hyperfoil.core.builders.Condition;

public class ConditionalHeaderHandler extends BaseDelegatingHeaderHandler {
   private final Condition condition;

   public ConditionalHeaderHandler(Condition condition, HeaderHandler[] handlers) {
      super(handlers);
      this.condition = condition;
   }

   @Override
   public void beforeHeaders(HttpRequest request) {
      if (condition.test(request.session)) {
         super.beforeHeaders(request);
      }
   }

   @Override
   public void handleHeader(HttpRequest request, CharSequence header, CharSequence value) {
      if (condition.test(request.session)) {
         super.handleHeader(request, header, value);
      }
   }

   @Override
   public void afterHeaders(HttpRequest request) {
      if (condition.test(request.session)) {
         super.afterHeaders(request);
      }
   }

   /**
    * Passes the headers to nested handler if the condition holds.
    * Note that the condition may be evaluated multiple times and therefore
    * any nested handlers should not change the results of the condition.
    */
   @MetaInfServices(HeaderHandler.Builder.class)
   @Name("conditional")
   public static class Builder extends BaseDelegatingHeaderHandler.Builder<Builder> {
      private Condition.TypesBuilder<Builder> condition = new Condition.TypesBuilder<>(this);

      @Embed
      public Condition.TypesBuilder<Builder> condition() {
         return condition;
      }

      @Override
      public ConditionalHeaderHandler build() {
         if (handlers.isEmpty()) {
            throw new BenchmarkDefinitionException("Conditional handler does not delegate to any handler.");
         }
         Condition condition = this.condition.buildCondition();
         if (condition == null) {
            throw new BenchmarkDefinitionException("Conditional handler must specify a condition.");
         }
         return new ConditionalHeaderHandler(condition, buildHandlers());
      }
   }
}
