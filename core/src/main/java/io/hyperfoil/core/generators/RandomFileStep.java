package io.hyperfoil.core.generators;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.BenchmarkData;
import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.Locator;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.config.StepBuilder;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.util.Util;

public class RandomFileStep implements Step, ResourceUtilizer {
   private final WeightedGenerator generator;
   private final byte[][] fileBytes;
   private final Access toVar;
   private final Access filenameVar;

   public RandomFileStep(WeightedGenerator generator, byte[][] fileBytes, Access toVar, Access filenameVar) {
      this.generator = generator;
      this.fileBytes = fileBytes;
      this.toVar = toVar;
      this.filenameVar = filenameVar;
   }

   @Override
   public boolean invoke(Session session) {
      int index = generator.randomIndex();
      toVar.setObject(session, fileBytes[index]);
      if (filenameVar != null) {
         filenameVar.setObject(session, generator.items()[index]);
      }
      return true;
   }

   @Override
   public void reserve(Session session) {
      toVar.declareObject(session);
      if (filenameVar != null) {
         filenameVar.declareObject(session);
      }
   }

   /**
    * Reads bytes from a randomly chosen file into a variable.
    * Two formats are supported:
    * Example 1 - without weights:
    * <code>
    * toVar: myVar
    * files:
    * - /path/to/file1.txt
    * - file2_relative_to_benchmark.txt
    * </code>
    * <p>
    * Example 2 - with weights (the second file will be returned twice as often):
    * <code>
    * toVar: myVar
    * files:
    * /path/to/file1.txt: 1
    * file2_relative_to_benchmark.txt: 2
    * </code>
    */
   @MetaInfServices(StepBuilder.class)
   @Name("randomFile")
   public static class Builder implements StepBuilder<Builder> {
      private String toVar;
      private WeightedGenerator.Builder weighted = new WeightedGenerator.Builder();
      private String filenameVar;

      /**
       * Potentially weighted list of files to choose from.
       *
       * @return Builder.
       */
      public WeightedGenerator.Builder files() {
         return weighted;
      }

      /**
       * Variable where the chosen byte array should be stored.
       *
       * @param var Variable name.
       * @return Self.
       */
      public Builder toVar(String var) {
         this.toVar = var;
         return this;
      }

      /**
       * Optional variable to store the filename of the random file.
       *
       * @param var Variable name.
       * @return Self.
       */
      public Builder filenameVar(String var) {
         this.filenameVar = var;
         return this;
      }

      @Override
      public List<Step> build() {
         WeightedGenerator generator = weighted.build();
         BenchmarkData data = Locator.current().benchmark().data();
         List<byte[]> fileBytes = new ArrayList<>();
         for (String file : generator.items()) {
            try {
               fileBytes.add(Util.toByteArray(data.readFile(file)));
            } catch (IOException e) {
               throw new BenchmarkDefinitionException("Cannot read bytes from file " + file);
            }
         }
         return Collections.singletonList(new RandomFileStep(generator, fileBytes.toArray(new byte[0][]), SessionFactory.access(toVar), SessionFactory.access(filenameVar)));
      }
   }
}
