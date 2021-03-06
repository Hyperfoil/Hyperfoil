package io.hyperfoil.core.steps;

import java.util.Collections;
import java.util.List;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.config.StepBuilder;
import io.hyperfoil.api.session.ObjectAccess;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.session.SharedData;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.core.builders.BaseStepBuilder;
import io.hyperfoil.core.session.IntVar;
import io.hyperfoil.core.session.ObjectVar;
import io.hyperfoil.core.session.SessionFactory;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class PullSharedMapStep implements Step, ResourceUtilizer {
   private static final Logger log = LogManager.getLogger(PullSharedMapStep.class);
   private static final boolean trace = log.isTraceEnabled();

   private final String key;
   private final ObjectAccess match;

   public PullSharedMapStep(String key, ObjectAccess match) {
      this.key = key;
      this.match = match;
   }

   @Override
   public boolean invoke(Session session) {
      SharedData.SharedMap sharedMap;
      if (match == null) {
         sharedMap = session.sharedData().pullMap(key);
         if (sharedMap == null) {
            if (trace) {
               log.trace("Did not find any shared map for key {}", key);
            }
            return true;
         }
      } else {
         Object value = match.getObject(session);
         sharedMap = session.sharedData().pullMap(key, match, value);
         if (sharedMap == null) {
            if (trace) {
               log.trace("Did not find any shared map for key {} matching {}={}", key, match, value);
            }
            return true;
         }
      }
      for (int i = 0; i < sharedMap.size(); ++i) {
         Session.Var var = sharedMap.key(i).getVar(session);
         switch (var.type()) {
            case OBJECT:
               ((ObjectVar) var).set(sharedMap.value(i));
               break;
            case INTEGER:
               ((IntVar) var).set((Integer) sharedMap.value(i));
               break;
         }
      }
      session.sharedData().releaseMap(key, sharedMap);
      return true;
   }

   @Override
   public void reserve(Session session) {
      session.sharedData().reserveMap(key, match, 0);
   }

   /**
    * Move values from a map shared across all sessions using the same executor into session variables.
    * <p>
    * The executor can host multiple shared maps, each holding an entry with several variables.
    * This step moves variables from either a random entry (if no <code>match</code> is set) or with an entry
    * that has the same value for given variable as the current session.
    * When data is moved to the current session the entry is dropped from the shared map.
    */
   @MetaInfServices(StepBuilder.class)
   @Name("pullSharedMap")
   public static class Builder extends BaseStepBuilder<Builder> {
      private String key;
      private String match;

      @Override
      public List<Step> build() {
         return Collections.singletonList(new PullSharedMapStep(key, SessionFactory.objectAccess(match)));
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
   }
}
