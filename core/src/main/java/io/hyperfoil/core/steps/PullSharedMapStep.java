package io.hyperfoil.core.steps;

import java.util.Collections;
import java.util.List;

import io.hyperfoil.api.config.Sequence;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.session.SharedData;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.config.BaseSequenceBuilder;
import io.hyperfoil.core.builders.BaseStepBuilder;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.function.SerializableSupplier;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class PullSharedMapStep implements Step, ResourceUtilizer {
   private static final Logger log = LoggerFactory.getLogger(PullSharedMapStep.class);
   private static final boolean trace = log.isTraceEnabled();

   private final String key;
   private final Access match;

   public PullSharedMapStep(String key, String match) {
      this.key = key;
      this.match = SessionFactory.access(match);
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
         sharedMap.key(i).setObject(session, sharedMap.value(i));
      }
      session.sharedData().releaseMap(key, sharedMap);
      return true;
   }

   @Override
   public void reserve(Session session) {
      session.sharedData().reserveMap(key, match, 0);
   }

   public static class Builder extends BaseStepBuilder {
      private String key;
      private String match;

      public Builder(BaseSequenceBuilder parent) {
         super(parent);
      }

      @Override
      public List<Step> build(SerializableSupplier<Sequence> sequence) {
         return Collections.singletonList(new PullSharedMapStep(key, match));
      }

      public Builder key(String key) {
         this.key = key;
         return this;
      }

      public Builder match(String match) {
         this.match = match;
         return this;
      }
   }
}
