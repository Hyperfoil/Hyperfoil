package io.hyperfoil.core.steps.data;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.InitFromParam;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.session.Action;
import io.hyperfoil.api.session.IntAccess;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.session.ThreadData;
import io.hyperfoil.core.session.SessionFactory;

public class GetSharedCounterAction implements Action, ResourceUtilizer {
   private static final Logger log = LogManager.getLogger(GetSharedCounterAction.class);
   private final String key;
   private final IntAccess toVar;

   public GetSharedCounterAction(String key, IntAccess toVar) {
      this.key = key;
      this.toVar = toVar;
   }

   @Override
   public void run(Session session) {
      ThreadData.SharedCounter counter = session.threadData().getCounter(key);
      if (counter.get() > Integer.MAX_VALUE) {
         log.warn("Shared counter value ({}) exceeds maximum integer; capping to {}", counter.get(), Integer.MAX_VALUE);
         toVar.setInt(session, Integer.MAX_VALUE);
      } else if (counter.get() < Integer.MIN_VALUE) {
         log.warn("Shared counter value ({}) exceeds minimum integer; capping to {}", counter.get(), Integer.MIN_VALUE);
         toVar.setInt(session, Integer.MIN_VALUE);
      } else {
         toVar.setInt(session, (int) counter.get());
      }
   }

   @Override
   public void reserve(Session session) {
      session.threadData().reserveCounter(key);
   }

   /**
    * Retrieves value from a counter shared by all sessions in the same executor and stores that in a session variable.
    * If the value exceeds allowed integer range (-2^31 .. 2^31 - 1) it is capped.
    */
   @MetaInfServices(Action.Builder.class)
   @Name("getSharedCounter")
   public static class Builder implements Action.Builder, InitFromParam<Builder> {
      private String key;
      private String toVar;

      /**
       * Uses the same name for key and variable name.
       *
       * @param param Both the key and variable name.
       * @return Self.
       */
      @Override
      public Builder init(String param) {
         return key(param).toVar(param);
      }

      /**
       * Identifier for the counter.
       *
       * @param key Name.
       * @return Self.
       */
      public Builder key(String key) {
         this.key = key;
         return this;
      }

      /**
       * Session variable for storing the value.
       *
       * @param toVar Variable name.
       * @return Self.
       */
      public Builder toVar(String toVar) {
         this.toVar = toVar;
         return this;
      }

      @Override
      public GetSharedCounterAction build() {
         return new GetSharedCounterAction(key, SessionFactory.intAccess(toVar));
      }
   }
}
