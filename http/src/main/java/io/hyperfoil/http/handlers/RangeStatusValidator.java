package io.hyperfoil.http.handlers;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.InitFromParam;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.http.api.HttpRequest;
import io.hyperfoil.http.api.StatusHandler;
import io.hyperfoil.impl.Util;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class RangeStatusValidator implements StatusHandler {
   private static final Logger log = LogManager.getLogger(RangeStatusValidator.class);

   public final int min;
   public final int max;

   public RangeStatusValidator(int min, int max) {
      this.min = min;
      this.max = max;
   }

   @Override
   public void handleStatus(HttpRequest request, int status) {
      boolean valid = status >= min && status <= max;
      if (!valid) {
         request.markInvalid();
         log.warn("#{} Sequence {}, request {} on connection {} received invalid status {}", request.session.uniqueId(),
               request.sequence(), request, request.connection(), status);
      }
   }

   /**
    * Marks requests that don't fall into the desired range as invalid.
    */
   @MetaInfServices(StatusHandler.Builder.class)
   @Name("range")
   public static class Builder implements StatusHandler.Builder, InitFromParam<Builder> {
      private int min = 200;
      private int max = 299;

      /**
       * @param param Single status code (<code>204</code>), masked code (<code>2xx</code>) or range (<code>200-399</code>).
       * @return Self.
       */
      @Override
      public Builder init(String param) {
         int xn = 0;
         for (int i = param.length() - 1; i >= 0; --i) {
            if (param.charAt(i) == 'x') {
               ++xn;
            } else break;
         }
         try {
            int dash = param.indexOf('-');
            if (dash >= 0) {
               min = Integer.parseInt(param.substring(0, dash).trim());
               max = Integer.parseInt(param.substring(dash + 1).trim());
            } else {
               int value = Integer.parseInt(param.substring(0, param.length() - xn));
               int mul = Util.pow(10, xn);
               min = value * mul;
               max = (value + 1) * mul - 1;
            }
         } catch (NumberFormatException e) {
            throw new BenchmarkDefinitionException("Cannot parse '" + param + "' as status range");
         }
         return this;
      }

      @Override
      public RangeStatusValidator build() {
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
}
