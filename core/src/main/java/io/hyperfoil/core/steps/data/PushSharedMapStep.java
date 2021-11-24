package io.hyperfoil.core.steps.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.ListBuilder;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.config.StepBuilder;
import io.hyperfoil.api.session.ObjectAccess;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.session.ThreadData;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.core.builders.BaseStepBuilder;
import io.hyperfoil.core.session.SessionFactory;

public class PushSharedMapStep implements Step, ResourceUtilizer {
   private final String key;
   private final ObjectAccess[] vars;

   public PushSharedMapStep(String key, ObjectAccess[] vars) {
      this.key = key;
      this.vars = vars;
   }

   @Override
   public boolean invoke(Session session) {
      ThreadData threadData = session.threadData();
      ThreadData.SharedMap sharedMap = threadData.newMap(key);
      for (int i = 0; i < vars.length; ++i) {
         Object value = vars[i].getObject(session);
         value = SharedDataHelper.unwrapVars(session, value);
         sharedMap.put(vars[i].key(), value);
      }
      threadData.pushMap(key, sharedMap);
      return true;
   }

   @Override
   public void reserve(Session session) {
      session.threadData().reserveMap(key, null, vars.length);
   }

   /**
    * Store values from session variables into a map shared across all sessions using the same executor into session variables.
    * <p>
    * The executor can host multiple shared maps, each holding an entry with several variables.
    * This step creates one entry in the map, copying values from session variables into the entry.
    */
   @MetaInfServices(StepBuilder.class)
   @Name("pushSharedMap")
   public static class Builder extends BaseStepBuilder<Builder> {
      private String key;
      private Collection<String> vars = new ArrayList<>();

      @Override
      public List<Step> build() {
         if (key == null || key.isEmpty()) {
            throw new BenchmarkDefinitionException("Invalid key: " + key);
         } else if (vars.isEmpty()) {
            throw new BenchmarkDefinitionException("No variables pushed for key " + key);
         }
         // While in this very step we will only read the session variables the Access instances are used
         // later in PullSharedMapStep to write the vars, too.
         // TODO: what it any of the vars is int?
         ObjectAccess[] accesses = vars.stream().map(SessionFactory::objectAccess).toArray(ObjectAccess[]::new);
         return Collections.singletonList(new PushSharedMapStep(key, accesses));
      }

      /**
       * Key identifying the shared map.
       *
       * @param key Identifier.
       * @return Self.
       */
      public Builder key(String key) {
         this.key = key;
         return this;
      }

      /**
       * List of variable names that should be stored in the entry.
       *
       * @return Builder.
       */
      public ListBuilder vars() {
         return vars::add;
      }
   }
}
