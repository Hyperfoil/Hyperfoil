package io.sailrocket.core.generators;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import io.sailrocket.api.config.ListBuilder;
import io.sailrocket.api.config.Step;
import io.sailrocket.api.session.Session;
import io.sailrocket.core.api.ResourceUtilizer;
import io.sailrocket.core.builders.BaseSequenceBuilder;
import io.sailrocket.core.builders.BaseStepBuilder;

public class RandomItemStep implements Step, ResourceUtilizer {
   private final String fromVar;
   private final String[] list;
   private final String var;

   public RandomItemStep(String fromVar, String[] list, String var) {
      this.fromVar = fromVar;
      this.list = list;
      this.var = var;
   }

   @Override
   public boolean invoke(Session session) {
      ThreadLocalRandom random = ThreadLocalRandom.current();
      Object item;
      if (list != null) {
         item = list[random.nextInt(list.length)];
      } else {
         Object data = session.getObject(fromVar);
         if (data != null && data.getClass().isArray()) {
            int length = Array.getLength(data);
            item = Array.get(data, random.nextInt(length));
         } else if (data instanceof List) {
            List dataList = (List) data;
            item = dataList.get(random.nextInt(dataList.size()));
         } else if (data instanceof Collection) {
            Collection dataCollection = (Collection) data;
            Iterator iterator = dataCollection.iterator();
            for (int i = random.nextInt(dataCollection.size()) - 1; i > 0; --i) {
               iterator.next();
            }
            item = iterator.next();
         } else {
            throw new IllegalStateException("Cannot fetch random item from collection stored under " + fromVar + ": " + data);
         }
      }
      session.setObject(var, item);
      return true;
   }

   @Override
   public void reserve(Session session) {
      session.declare(var);
   }

   public static class Builder extends BaseStepBuilder {
      private String fromVar;
      private List<String> list = new ArrayList<>();
      private String var;

      public Builder(BaseSequenceBuilder parent) {
         super(parent);
      }

      @Override
      public List<Step> build() {
         return Collections.singletonList(new RandomItemStep(fromVar, list.isEmpty() ? null : list.toArray(new String[0]), var));
      }

      public Builder fromVar(String fromVar) {
         this.fromVar = fromVar;
         return this;
      }

      public ListBuilder list() {
         return list::add;
      }

      public Builder var(String var) {
         this.var = var;
         return this;
      }
   }
}
