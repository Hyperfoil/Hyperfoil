package io.hyperfoil.core.steps;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.InitFromParam;
import io.hyperfoil.api.config.ListBuilder;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.session.Action;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.generators.Pattern;

/**
 * This is a debugging step.
 */
public class LogAction implements Action {
   private static final Logger log = LogManager.getLogger(LogAction.class);

   private final Pattern message;

   public LogAction(Pattern message) {
      this.message = message;
   }

   @Override
   public void run(Session session) {
      log.info(message.apply(session));
   }

   /**
    * Log a message and variable values.
    */
   @MetaInfServices(Action.Builder.class)
   @Name("log")
   public static class Builder<P> implements Action.Builder, InitFromParam<Builder<P>> {
      String message;
      List<String> vars = new ArrayList<>();

      /**
       * @param param A pattern for
       *        <a href="https://hyperfoil.io/docs/user-guide/benchmark/variables#string-interpolation">string
       *        interpolation</a>.
       * @return Self.
       */
      @Override
      public Builder<P> init(String param) {
         return message(param);
      }

      /**
       * Message format pattern. Use
       * <a href="https://hyperfoil.io/docs/user-guide/benchmark/variables#string-interpolation">string interpolation</a>
       * for variables.
       *
       * @param message Message format pattern.
       * @return Self.
       */
      public Builder<P> message(String message) {
         this.message = message;
         return this;
      }

      /**
       * List of variables to be logged.
       *
       * @return Builder.
       */
      @Deprecated
      public ListBuilder vars() {
         return vars::add;
      }

      // the ignored parameter removes this method from YAML-based configuration
      public Builder<P> addVar(String var, Void ignored) {
         vars.add(var);
         return this;
      }

      @Override
      public LogAction build() {
         if (message == null) {
            throw new BenchmarkDefinitionException("Missing message");
         } else if (vars.isEmpty()) {
            return new LogAction(new Pattern(message, false, true));
         }
         StringBuilder msgBuilder = new StringBuilder();
         int from = 0;
         for (String var : vars) {
            int index = message.indexOf("{}", from);
            if (index >= 0) {
               msgBuilder.append(message, from, index);
               msgBuilder.append("${").append(var).append("}");
               from = index + 2;
            } else {
               throw new BenchmarkDefinitionException(
                     "Missing position for variable " + var + " ('{}') in log message '" + message + "'");
            }
         }
         msgBuilder.append(message.substring(from));
         return new LogAction(new Pattern(msgBuilder.toString(), false, true));
      }
   }
}
