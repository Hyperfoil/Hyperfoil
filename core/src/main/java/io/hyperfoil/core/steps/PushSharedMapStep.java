package io.hyperfoil.core.steps;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.ListBuilder;
import io.hyperfoil.api.config.Sequence;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.session.SharedData;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.config.BaseSequenceBuilder;
import io.hyperfoil.core.builders.BaseStepBuilder;
import io.hyperfoil.function.SerializableSupplier;

public class PushSharedMapStep implements Step, ResourceUtilizer {
   private final String key;
   private final String[] vars;

   public PushSharedMapStep(String key, String[] vars) {
      this.key = key;
      this.vars = vars;
   }

   @Override
   public boolean invoke(Session session) {
      SharedData sharedData = session.sharedData();
      SharedData.SharedMap sharedMap = sharedData.newMap(key);
      for (String var : vars) {
         sharedMap.put(var, session.getObject(var));
      }
      sharedData.pushMap(key, sharedMap);
      return true;
   }

   @Override
   public void reserve(Session session) {
      session.sharedData().reserveMap(key, null, vars.length);
   }

   public static class Builder extends BaseStepBuilder {
      private String key;
      private Collection<String> vars = new ArrayList<>();

      public Builder(BaseSequenceBuilder parent) {
         super(parent);
      }

      @Override
      public List<Step> build(SerializableSupplier<Sequence> sequence) {
         if (vars.isEmpty()) {
            throw new BenchmarkDefinitionException("No variables pushed for key " + key);
         }
         return Collections.singletonList(new PushSharedMapStep(key, vars.toArray(new String[0])));
      }

      public Builder key(String key) {
         this.key = key;
         return this;
      }

      public ListBuilder vars() {
         return vars::add;
      }
   }
}
