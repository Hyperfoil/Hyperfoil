package io.hyperfoil.core.steps;

import io.hyperfoil.api.config.InitFromParam;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.config.StepBuilder;
import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.session.ObjectAccess;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.builders.BaseStepBuilder;
import io.hyperfoil.core.session.SessionFactory;

import org.kohsuke.MetaInfServices;

import java.util.Collections;
import java.util.List;

/**
 * This class implements a {@link Step} that bumps the current time in milliseconds as {@link String} to a variable.
 */
public class TimestampStep implements Step {
   private final ObjectAccess toVar;

   public TimestampStep(ObjectAccess toVar) {
      this.toVar = toVar;
   }

   @Override
   public boolean invoke(Session session) {
      toVar.setObject(session, String.valueOf(System.currentTimeMillis()));
      return true;
   }

   /**
    * Stores the current time in milliseconds as string to a session variable.
    */
   @MetaInfServices(StepBuilder.class)
   @Name("timestamp")
   public static class Builder extends BaseStepBuilder<Builder> implements InitFromParam<Builder> {
      private String toVar;

      /**
       * @param param Variable name.
       * @return Self.
       */
      @Override
      public Builder init(String param) {
         return toVar(param);
      }

      /**
       * Target variable name.
       *
       * @param toVar Variable name.
       * @return Self.
       */
      public Builder toVar(String toVar) {
         this.toVar = toVar;
         return this;
      }

      @Override
      public List<Step> build() {
         if (toVar == null) {
            throw new BenchmarkDefinitionException("Missing toVar attribute");
         }
         return Collections.singletonList(new TimestampStep(SessionFactory.objectAccess(toVar)));
      }
   }
}
