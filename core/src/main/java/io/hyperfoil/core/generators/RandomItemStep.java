package io.hyperfoil.core.generators;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.InitFromParam;
import io.hyperfoil.api.config.ListBuilder;
import io.hyperfoil.api.config.Locator;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.config.PairBuilder;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.config.StepBuilder;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.core.builders.BaseStepBuilder;
import io.hyperfoil.core.session.ObjectVar;
import io.hyperfoil.core.session.SessionFactory;

public class RandomItemStep implements Step, ResourceUtilizer {
   private final Access fromVar;
   private final double[] cummulativeProbs;
   private final String[] list;
   private final Access toVar;

   public RandomItemStep(String fromVar, double[] cummulativeProbs, String[] list, String toVar) {
      this.fromVar = SessionFactory.access(fromVar);
      this.cummulativeProbs = cummulativeProbs;
      this.list = list;
      this.toVar = SessionFactory.access(toVar);
   }

   @Override
   public boolean invoke(Session session) {
      ThreadLocalRandom random = ThreadLocalRandom.current();
      Object item;
      if (list != null) {
         int index;
         if (cummulativeProbs != null) {
            assert cummulativeProbs.length == list.length - 1;
            index = Arrays.binarySearch(cummulativeProbs, random.nextDouble());
            if (index < 0) {
               index = -index - 1;
            }
         } else {
            index = random.nextInt(list.length);
         }
         item = list[index];
      } else {
         assert cummulativeProbs == null;
         Object data = fromVar.getObject(session);
         Object element;
         if (data instanceof ObjectVar[]) {
            // Assume that first unset variable denotes end of set variables
            ObjectVar[] array = (ObjectVar[]) data;
            int length = array.length;
            for (int i = 0; i < array.length; ++i) {
               if (!array[i].isSet()) {
                  length = i;
                  break;
               }
            }
            element = array[random.nextInt(length)];
         } else if (data != null && data.getClass().isArray()) {
            int length = Array.getLength(data);
            element = Array.get(data, random.nextInt(length));
         } else if (data instanceof List) {
            List dataList = (List) data;
            element = dataList.get(random.nextInt(dataList.size()));
         } else if (data instanceof Collection) {
            Collection dataCollection = (Collection) data;
            Iterator iterator = dataCollection.iterator();
            for (int i = random.nextInt(dataCollection.size()) - 1; i > 0; --i) {
               iterator.next();
            }
            element = iterator.next();
         } else {
            throw new IllegalStateException("Cannot fetch random item from collection stored under " + fromVar + ": " + data);
         }
         if (element instanceof ObjectVar) {
            item = ((ObjectVar) element).objectValue(session);
         } else {
            throw new IllegalStateException("Collection in " + fromVar + " should store ObjectVars, but it stores " + element);
         }
      }
      toVar.setObject(session, item);
      return true;
   }

   @Override
   public void reserve(Session session) {
      toVar.declareObject(session);
   }

   /**
    * Stores random item from a list or array into session variable.
    */
   @MetaInfServices(StepBuilder.class)
   @Name("randomItem")
   public static class Builder extends BaseStepBuilder<Builder> implements InitFromParam<Builder> {
      private Locator locator;
      private String fromVar;
      private List<String> list = new ArrayList<>();
      private Map<String, Double> weighted = new HashMap<>();
      private String file;
      private String toVar;

      @Override
      public Builder setLocator(Locator locator) {
         // Note: copy() is not overridden as we use locator only to read external file.
         this.locator = locator;
         return this;
      }

      /**
       * @param toFrom Use `toVar <- fromVar` where fromVar is an array/collection.
       * @return Self.
       */
      @Override
      public Builder init(String toFrom) {
         if (toFrom == null) {
            return this;
         }
         String[] parts = toFrom.split("<-");
         if (parts.length != 2) {
            throw new BenchmarkDefinitionException("Expecting format toVar <- fromVar");
         }
         toVar = parts[0].trim();
         fromVar = parts[1].trim();
         return this;
      }

      @Override
      public List<Step> build() {
         if (fromVar != null && (!list.isEmpty() || !weighted.isEmpty() || file != null)) {
            throw new BenchmarkDefinitionException("randomItem cannot combine `fromVar` and `list` or `file`");
         } else if (file != null && !(list.isEmpty() && weighted.isEmpty())) {
            throw new BenchmarkDefinitionException("randomItem cannot combine `list` and `file`");
         }
         List<String> list = new ArrayList<>(this.list);
         if (file != null) {
            try (InputStream inputStream = locator.scenario().endScenario().endPhase().data().readFile(file)) {
               if (inputStream == null) {
                  throw new BenchmarkDefinitionException("Cannot load file `" + file + "` for randomItem (not found).");
               }
               try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                  String line;
                  while ((line = reader.readLine()) != null) {
                     if (!line.isEmpty()) {
                        list.add(line);
                     }
                  }
               }
            } catch (IOException e) {
               throw new BenchmarkDefinitionException("Cannot load file `" + file + "` for randomItem.", e);
            }
         }
         double[] cummulativeProbs = null;
         if (!weighted.isEmpty()) {
            if (!list.isEmpty()) {
               throw new BenchmarkDefinitionException("Cannot combine weighted and not-weighted items.");
            }
            cummulativeProbs = new double[weighted.size() - 1];
            double normalizer = weighted.values().stream().mapToDouble(Double::doubleValue).sum();
            double acc = 0;
            int i = 0;
            for (Map.Entry<String, Double> entry : weighted.entrySet()) {
               acc += entry.getValue() / normalizer;
               if (i < weighted.size() - 1) {
                  cummulativeProbs[i++] = acc;
               } else {
                  assert acc > 0.999 && acc <= 1.001;
               }
               list.add(entry.getKey());
            }
         }
         if (fromVar == null && list.isEmpty()) {
            throw new BenchmarkDefinitionException("randomItem has empty list and `fromVar` was not defined.");
         }
         if (fromVar != null && fromVar.isEmpty()) {
            throw new BenchmarkDefinitionException("fromVar is empty");
         } else if (toVar.isEmpty()) {
            throw new BenchmarkDefinitionException("toVar is empty");
         }
         return Collections.singletonList(new RandomItemStep(fromVar, cummulativeProbs, list.isEmpty() ? null : list.toArray(new String[0]), toVar));
      }

      /**
       * Variable containing an array or list.
       *
       * @param fromVar Variable name.
       * @return Self.
       */
      public Builder fromVar(String fromVar) {
         this.fromVar = fromVar;
         return this;
      }

      /**
       * Potentially weighted list of items to choose from.
       *
       * @return Builder.
       */
      public ItemBuilder list() {
         return new ItemBuilder();
      }

      /**
       * Variable where the chosen item should be stored.
       *
       * @param var Variable name.
       * @return Self.
       */
      public Builder toVar(String var) {
         this.toVar = var;
         return this;
      }

      /**
       * This file will be loaded into memory and the step will choose on line as the item.
       *
       * @param file Path to the file.
       * @return Self.
       */
      public Builder file(String file) {
         this.file = file;
         return this;
      }

      public class ItemBuilder extends PairBuilder.OfDouble implements ListBuilder {
         @Override
         public void nextItem(String item) {
            list.add(item);
         }

         /**
          * Item as the key and weight (arbitrary floating-point number, defaults to 1.0) as the value.
          *
          * @param item   Item.
          * @param weight Weight.
          */
         @Override
         public void accept(String item, Double weight) {
            if (weighted.putIfAbsent(item, weight) != null) {
               throw new BenchmarkDefinitionException("Duplicate item '" + item + "' in randomItem step!");
            }
         }
      }
   }
}
