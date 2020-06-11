package io.hyperfoil.core.steps;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.ListBuilder;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.config.StepBuilder;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.session.SharedData;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.core.builders.BaseStepBuilder;
import io.hyperfoil.core.session.SessionFactory;

public class PushSharedMapStep implements Step, ResourceUtilizer {
   private final String key;
   private final Access[] vars;

   public PushSharedMapStep(String key, Access[] vars) {
      this.key = key;
      this.vars = vars;
   }

   @Override
   public boolean invoke(Session session) {
      SharedData sharedData = session.sharedData();
      SharedData.SharedMap sharedMap = sharedData.newMap(key);
      for (int i = 0; i < vars.length; ++i) {
         sharedMap.put(vars[i], vars[i].getObject(session));
      }
      sharedData.pushMap(key, sharedMap);
      return true;
   }

   @Override
   public void reserve(Session session) {
      session.sharedData().reserveMap(key, null, vars.length);
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
         if (vars.isEmpty()) {
            throw new BenchmarkDefinitionException("No variables pushed for key " + key);
         }
         final String[] vars1 = vars.toArray(new String[0]);
         return Collections.singletonList(new PushSharedMapStep(key, Stream.of(vars1).map(SessionFactory::access).toArray(Access[]::new)));
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
