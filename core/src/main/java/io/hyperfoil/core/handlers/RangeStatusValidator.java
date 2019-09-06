package io.hyperfoil.core.handlers;


import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.Locator;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.connection.Request;
import io.hyperfoil.api.http.StatusHandler;
import io.hyperfoil.function.SerializableSupplier;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class RangeStatusValidator implements StatusHandler {
   private static final Logger log = LoggerFactory.getLogger(RangeStatusValidator.class);

   public final int min;
   public final int max;

   public RangeStatusValidator(int min, int max) {
      this.min = min;
      this.max = max;
   }

   @Override
   public void handleStatus(Request request, int status) {
      boolean valid = status >= min && status <= max;
      if (!valid) {
         request.markInvalid();
         log.warn("#{} Sequence {}, connection {} received invalid status {}", request.session.uniqueId(),
               request.sequence(), request.connection(), status);
      }
   }

   /**
    * Marks requests that don't fall into the desired range as invalid.
    */
   public static class Builder implements StatusHandler.Builder {
      private int min = 200;
      private int max = 299;

      @Override
      public RangeStatusValidator build(SerializableSupplier<? extends Step> step) {
         return new RangeStatusValidator(min, max);
      }

      /**
       * Lowest accepted status code.
       *
       * @param min Minimum status (inclusive).
       * @return Self.
       */
      public Builder min(int min) {
         this.min = min;
         return this;
      }

      /**
       * Highest accepted status code.
       *
       * @param max Maximum status (inclusive)
       * @return Self.
       */
      public Builder max(int max) {
         this.max = max;
         return this;
      }
   }

   @MetaInfServices(StatusHandler.BuilderFactory.class)
   public static class BuilderFactory implements StatusHandler.BuilderFactory {
      @Override
      public String name() {
         return "range";
      }

      @Override
      public boolean acceptsParam() {
         return true;
      }

      /**
       * @param locator Locator.
       * @param param Single status code (<code>204</code>), masked code (<code>2xx</code>) or range (<code>200-399</code>).
       * @return Builder.
       */
      @Override
      public Builder newBuilder(Locator locator, String param) {
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
                  return new Builder().min(min).max(max);
               } else {
                  int value = Integer.parseInt(param.substring(0, param.length() - xn));
                  int mul = pow(10, xn);
                  int min = value * mul;
                  int max = (value + 1) * mul - 1;
                  return new Builder().min(min).max(max);
               }
            } catch (NumberFormatException e) {
               throw new BenchmarkDefinitionException("Cannot parse '" + param + "' as status range");
            }
         }
         return new Builder();
      }

      private int pow(int base, int exp) {
         int res = 1;
         while (exp-- > 0) res *= base;
         return res;
      }
   }
}
