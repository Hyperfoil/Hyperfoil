package io.hyperfoil.core.steps.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.ListBuilder;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.config.PairBuilder;
import io.hyperfoil.api.session.Action;
import io.hyperfoil.api.session.ReadAccess;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.data.GlobalCounters;
import io.hyperfoil.core.session.SessionFactory;

public class PublishGlobalCountersAction implements Action {
   private static final Logger log = LogManager.getLogger(PublishGlobalCountersAction.class);

   private final String key;
   private final String[] names;
   private final ReadAccess[] vars;

   public PublishGlobalCountersAction(String key, String[] names, ReadAccess[] vars) {
      assert names.length == vars.length;
      this.key = key;
      this.names = names;
      this.vars = vars;
   }

   @Override
   public void run(Session session) {
      Map<String, Long> counters = new HashMap<>();
      for (int i = 0; i < names.length; ++i) {
         Session.Var var = vars[i].getVar(session);
         switch (var.type()) {
            case OBJECT:
               Object obj = var.objectValue(session);
               if (obj instanceof String) {
                  try {
                     long value = Long.parseLong((String) obj);
                     counters.put(names[i], value);
                  } catch (NumberFormatException e) {
                     log.warn("#{}: Cannot parse {} obj into long value", session.uniqueId(), obj);
                  }
               } else {
                  log.warn("#{}: Cannot parse {} obj into long value", session.uniqueId(), obj);
               }
               break;
            case INTEGER:
               counters.put(names[i], (long) var.intValue(session));
               break;
         }
      }
      session.globalData().publish(session.phase().definition().name(), key, new GlobalCounters(counters));
   }

   /**
    * Gathers values from session variables and publishes them globally (to all agents). <br>
    * You can name the counters individually (example 1) or use the variable names (example 2):
    *
    * <pre>
    * <code>
    * # Example 1:
    * - publishGlobalCounters:
    *     key: myKey
    *     vars: [ foo, bar ]
    * # Example 2:
    * - publishGlobalCounters:
    *     key: someOtherKey
    *     vars:
    *     - foo: myFoo
    *     - bar: bbb
    * </code>
    * </pre>
    */
   @MetaInfServices(Action.Builder.class)
   @Name("publishGlobalCounters")
   public static class Builder implements Action.Builder {
      private String key;
      private List<String> names = new ArrayList<>();
      private List<String> vars = new ArrayList<>();

      /**
       * Identifier of the global record.
       *
       * @param key Identifier name.
       * @return Self.
       */
      public Builder key(String key) {
         this.key = key;
         return this;
      }

      /**
       * List of names and session variables.
       *
       * @return Builder.
       */
      public VarsBuilder vars() {
         return new VarsBuilder();
      }

      @Override
      public Action build() {
         if (key == null || key.isEmpty()) {
            throw new BenchmarkDefinitionException("Invalid key: " + key);
         } else if (names.isEmpty() || vars.isEmpty()) {
            throw new BenchmarkDefinitionException("No counters!");
         }
         return new PublishGlobalCountersAction(key, names.toArray(new String[0]),
               vars.stream().map(SessionFactory::readAccess).toArray(ReadAccess[]::new));
      }

      public class VarsBuilder extends PairBuilder.OfString implements ListBuilder {
         @Override
         public void nextItem(String key) {
            names.add(key);
            vars.add(key);
         }

         @Override
         public void accept(String name, String var) {
            names.add(name);
            vars.add(var);
         }
      }
   }
}
