package io.hyperfoil.core.generators;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.InitFromParam;
import io.hyperfoil.api.config.Locator;
import io.hyperfoil.api.config.Name;
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
   private final WeightedGenerator generator;
   private final Access toVar;

   public RandomItemStep(Access fromVar, WeightedGenerator generator, Access toVar) {
      this.fromVar = fromVar;
      this.generator = generator;
      this.toVar = toVar;
   }

   @Override
   public boolean invoke(Session session) {
      ThreadLocalRandom random = ThreadLocalRandom.current();
      Object item;
      if (generator != null) {
         assert fromVar == null;
         item = generator.randomItem();
      } else {
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
            List<?> dataList = (List<?>) data;
            element = dataList.get(random.nextInt(dataList.size()));
         } else if (data instanceof Collection) {
            Collection<?> dataCollection = (Collection<?>) data;
            Iterator<?> iterator = dataCollection.iterator();
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
      private String fromVar;
      private WeightedGenerator.Builder<Builder> weighted;
      private String file;
      private String toVar;

      /**
       * @param toFrom Use `toVar &lt;- fromVar` where fromVar is an array/collection.
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
         if (toVar == null || toVar.isEmpty()) {
            throw new BenchmarkDefinitionException("toVar is empty");
         }
         long usedProperties = Stream.of(file, weighted, fromVar).filter(Objects::nonNull).count();
         if (usedProperties > 1) {
            throw new BenchmarkDefinitionException("randomItem cannot combine `fromVar` and `list` or `file`");
         } else if (usedProperties == 0) {
            throw new BenchmarkDefinitionException("randomItem must define one of: `fromVar`, `list` or `file`");
         }
         WeightedGenerator generator;
         if (weighted != null) {
            generator = weighted.build();
         } else if (file != null) {
            List<String> list = new ArrayList<>();
            try (InputStream inputStream = Locator.current().benchmark().data().readFile(file)) {
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
            generator = new WeightedGenerator(null, list.toArray(new String[0]));
         } else {
            if (fromVar.isEmpty()) {
               throw new BenchmarkDefinitionException("fromVar is empty");
            }
            generator = null;
         }

         return Collections.singletonList(new RandomItemStep(SessionFactory.access(fromVar), generator, SessionFactory.access(toVar)));
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
      public WeightedGenerator.Builder<Builder> list() {
         if (weighted == null) {
            weighted = new WeightedGenerator.Builder<>(this);
         }
         return weighted;
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
   }
}
