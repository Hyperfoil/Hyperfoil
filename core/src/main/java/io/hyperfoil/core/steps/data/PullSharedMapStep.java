package io.hyperfoil.core.steps.data;

import java.util.ArrayList;
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

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class PullSharedMapStep implements Step, ResourceUtilizer {
   private static final Logger log = LogManager.getLogger(PullSharedMapStep.class);
   private static final boolean trace = log.isTraceEnabled();

   private final String key;
   private final ObjectAccess match;
   private final ObjectAccess[] vars;

   public PullSharedMapStep(String key, ObjectAccess match, ObjectAccess[] vars) {
      this.key = key;
      this.match = match;
      this.vars = vars;
   }

   @Override
   public boolean invoke(Session session) {
      ThreadData.SharedMap sharedMap;
      if (match == null) {
         sharedMap = session.threadData().pullMap(key);
         if (sharedMap == null) {
            if (trace) {
               log.trace("Did not find any shared map for key {}", key);
            }
            return true;
         }
      } else {
         Object value = match.getObject(session);
         sharedMap = session.threadData().pullMap(key, match, value);
         if (sharedMap == null) {
            if (trace) {
               log.trace("Did not find any shared map for key {} matching {}={}", key, match, value);
            }
            return true;
         }
      }
      for (ObjectAccess access : vars) {
         Object value = sharedMap.get(access.key());
         access.setObject(session, value);
      }
      session.threadData().releaseMap(key, sharedMap);
      return true;
   }

   @Override
   public void reserve(Session session) {
      session.threadData().reserveMap(key, match, 0);
   }

   /**
    * Move values from a map shared across all sessions using the same executor into session variables.
    * <p>
    * The executor can host multiple shared maps, each holding an entry with several variables.
    * This step moves variables from either a random entry (if no <code>match</code> is set) or with an entry
    * that has the same value for given variable as the current session.
    * When data is moved to the current session the entry is dropped from the shared map. If the map contains
    * records for which the {@link #vars()} don't contain a destination variable the contents is lost.
    */
   @MetaInfServices(StepBuilder.class)
   @Name("pullSharedMap")
   public static class Builder extends BaseStepBuilder<Builder> {
      private String key;
      private String match;
      private List<String> vars = new ArrayList<>();

      @Override
      public List<Step> build() {
         if (key == null || key.isEmpty()) {
            throw new BenchmarkDefinitionException("Invalid key: " + key);
         } else if (vars.isEmpty()) {
            throw new BenchmarkDefinitionException("You have to set at least one variable.");
         }
         return Collections.singletonList(new PullSharedMapStep(key, SessionFactory.objectAccess(match),
               vars.stream().map(SessionFactory::objectAccess).toArray(ObjectAccess[]::new)));
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
       * Name of the session variable that stores value identifying the entry in the shared map.
       *
       * @param match Variable name.
       * @return Self.
       */
      public Builder match(String match) {
         this.match = match;
         return this;
      }

      /**
       * List of variables the map should be pulled into.
       *
       * @return List builder.
       */
      public ListBuilder vars() {
         return vars::add;
      }
   }
}
