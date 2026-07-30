package io.hyperfoil.example;

import java.util.Collections;
import java.util.List;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.InitFromParam;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.config.StepBuilder;
import io.hyperfoil.api.session.IntAccess;
import io.hyperfoil.api.session.ReadAccess;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.builders.BaseStepBuilder;
import io.hyperfoil.core.session.SessionFactory;

/**
 * Example step for <a href="http://hyperfoil.io/quickstart/quickstart8">Custom steps tutorial</a>
 */
public class DivideStep implements Step {
   // All fields in a step are immutable, any state must be stored in the Session
   private final ReadAccess fromVar;
   private final IntAccess toVar;
   private final int divisor;

   public DivideStep(ReadAccess fromVar, IntAccess toVar, int divisor) {
      // Variables in session are not accessed directly using map lookup but
      // through the Access objects. This is necessary as the scenario can use
      // some simple expressions that are parsed when the scenario is built
      // (in this constructor), not at runtime.
      this.fromVar = fromVar;
      this.toVar = toVar;
      this.divisor = divisor;
   }

   @Override
   public boolean invoke(Session session) {
      // This step will block until the variable is set, rather than
      // throwing an error or defaulting the value.
      if (!fromVar.isSet(session)) {
         return false;
      }
      // Session can store either objects or integers. Using int variables is
      // more efficient as it prevents repeated boxing and unboxing.
      int value = fromVar.getInt(session);
      toVar.setInt(session, value / divisor);
      return true;
   }

   // Make this builder loadable as service
   @MetaInfServices(StepBuilder.class)
   // This is the step name that will be used in the YAML
   @Name("divide")
   public static class Builder extends BaseStepBuilder<Builder> implements InitFromParam<Builder> {
      // Contrary to the step fields in builder are mutable
      private String fromVar;
      private String toVar;
      private int divisor;

      // Let's permit a short-form definition that will store the result
      // in the same variable. Note that the javadoc @param is used to generate external documentation.

      /**
       * @param param Use myVar /= constant
       */
      @Override
      public Builder init(String param) {
         int divIndex = param.indexOf("/=");
         if (divIndex < 0) {
            throw new BenchmarkDefinitionException("Invalid inline definition: " + param);
         }
         try {
            divisor(Integer.parseInt(param.substring(divIndex + 2).trim()));
         } catch (NumberFormatException e) {
            throw new BenchmarkDefinitionException("Invalid inline definition: " + param, e);
         }
         String var = param.substring(0, divIndex).trim();
         return fromVar(var).toVar(var);
      }

      // All fields are set in fluent setters - this helps when the scenario
      // is defined through programmatic configuration.
      // When parsing YAML the methods are invoked through reflection;
      // the attribute name is used for the method lookup.
      public Builder fromVar(String fromVar) {
         this.fromVar = fromVar;
         return this;
      }

      public Builder toVar(String toVar) {
         this.toVar = toVar;
         return this;
      }

      // The parser can automatically convert primitive types and enums.
      public Builder divisor(int divisor) {
         this.divisor = divisor;
         return this;
      }

      @Override
      public List<Step> build() {
         // You can ignore the sequence parameter; this is used only in steps
         // that require access to the parent sequence at runtime.
         if (fromVar == null || toVar == null || divisor == 0) {
            // Here is a good place to check that the attributes are sane.
            throw new BenchmarkDefinitionException("Missing one of the required attributes!");
         }
         // The builder has a bit more flexibility and it can create more than
         // one step at once.
         return Collections.singletonList(new DivideStep(
               SessionFactory.readAccess(fromVar), SessionFactory.intAccess(toVar), divisor));
      }
   }
}
