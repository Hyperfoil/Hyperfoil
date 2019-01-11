package io.sailrocket.core.extractors;

import java.util.function.Consumer;

import org.kohsuke.MetaInfServices;

import io.sailrocket.api.config.BenchmarkDefinitionException;
import io.sailrocket.api.config.ServiceLoadedBuilder;
import io.sailrocket.api.connection.Request;
import io.sailrocket.api.http.StatusValidator;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class RangeStatusValidator implements StatusValidator {
   private static final Logger log = LoggerFactory.getLogger(RangeStatusValidator.class);

   public final int min;
   public final int max;

   public RangeStatusValidator(int min, int max) {
      this.min = min;
      this.max = max;
   }

   @Override
   public boolean validate(Request request, int status) {
      boolean valid = status >= min && status <= max;
      if (!valid) {
         log.warn("#{} Sequence {}, connection {} received invalid status {}", request.session.uniqueId(),
               request.sequence(), request.connection(), status);
      }
      return valid;
   }

   public static class Builder extends ServiceLoadedBuilder.Base<StatusValidator> {
      private int min = 200;
      private int max = 299;

      protected Builder(Consumer<StatusValidator> buildTarget) {
         super(buildTarget);
      }

      @Override
      protected StatusValidator build() {
         return new RangeStatusValidator(min, max);
      }

      public Builder min(int min) {
         this.min = min;
         return this;
      }

      public Builder max(int max) {
         this.max = max;
         return this;
      }
   }

   @MetaInfServices(StatusValidator.BuilderFactory.class)
   public static class BuilderFactory implements StatusValidator.BuilderFactory {
      @Override
      public String name() {
         return "range";
      }

      @Override
      public ServiceLoadedBuilder newBuilder(Consumer<StatusValidator> buildTarget, String param) {
         if (param != null) {
            int xn = 0;
            for (int i = param.length() - 1; i >= 0; --i) {
               if (param.charAt(i) == 'x') {
                  ++xn;
               } else break;
            }
            try {
               int dash = param.indexOf('-');
               if (dash >= 0) {
                  int min = Integer.parseInt(param.substring(0, dash).trim());
                  int max = Integer.parseInt(param.substring(dash + 1).trim());
                  return new Builder(buildTarget).min(min).max(max);
               } else {
                  int value = Integer.parseInt(param.substring(0, param.length() - xn));
                  int mul = pow(10, xn);
                  int min = value * mul;
                  int max = (value + 1) * mul - 1;
                  return new Builder(buildTarget).min(min).max(max);
               }
            } catch (NumberFormatException e) {
               throw new BenchmarkDefinitionException("Cannot parse '" + param + "' as status range");
            }
         }
         return new Builder(buildTarget);
      }

      private int pow(int base, int exp) {
         int res = 1;
         while (exp-- > 0) res *= base;
         return res;
      }
   }
}