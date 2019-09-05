package io.hyperfoil.core.generators;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import io.hyperfoil.api.config.BaseSequenceBuilder;
import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.PairBuilder;
import io.hyperfoil.api.config.Sequence;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.builders.BaseStepBuilder;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.function.SerializableSupplier;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

/**
 * A class that will initialise, build and randomly select a single row of data.
 * The row is exposed as columns.
 *
 */
public class RandomCsvRowStep implements Step, ResourceUtilizer  {
   private static final Logger log = LoggerFactory.getLogger(RandomCsvRowStep.class);
   private String[][] rows ;
   private final Access[] columnVars;

   /**
    * ctor
    * @param fromVar data provided to step via a variable
    * @param cummulativeProbs
    * @param rowsList processed declarative data
    * @param columns currently active columns
    * @param var declarative data
    */
   public RandomCsvRowStep(String[][] rows, List<String> vars) {
      this.rows = rows;
      this.columnVars = vars.stream().map(SessionFactory::access).toArray(Access[]::new);
   }

   @Override
   public boolean invoke(Session session) {
      // columns provided by csv
      ThreadLocalRandom random = ThreadLocalRandom.current();
      int next = random.nextInt(rows.length);
      int last = columnVars.length;
      for (int i = 0, j = 0; i < last; i += 1) {
         if (rows[next][i] != null) {
            columnVars[j++].setObject(session, rows[next][i]);
         }
      }
      return true;
   }

   @Override
   public void reserve(Session session) {
      Arrays.asList(columnVars).forEach(var -> var.declareObject(session));
   }

   /**
    * Stores random row from a CSV-formatted file to variables.
    */
   public static class Builder extends BaseStepBuilder {
      private String file;
      private boolean skipComments;
      private boolean isQuotesRemoved;
      private Map<String, Integer> builderColumns = new HashMap<>();
      private int maxSize = 0;

      public Builder(BaseSequenceBuilder parent) {
         super(parent);
      }

      @Override
      public List<Step> build(SerializableSupplier<Sequence> sequence) {
         File f = new File(file);
         if (!f.exists()) {
            throw new BenchmarkDefinitionException("Supplied file cannot be found on system");
         }
         List<String[]> rows;
         try (BufferedReader reader = Files.newBufferedReader(Paths.get(f.getAbsolutePath()))) {
            Predicate<String> comments = s -> (skipComments ? !(s.trim().startsWith("#")) : true);
            rows = reader.lines()
                  .filter(comments)
                  .map(s -> isQuotesRemoved ? s.replaceAll("\"", "") : s)
                  .map(line -> line.split(","))
                  .collect(Collectors.toList());
         } catch (IOException ioe) {
            throw new BenchmarkDefinitionException(ioe.getMessage());
         }
         if (rows.isEmpty()) {
            throw new BenchmarkDefinitionException("Missing CSV row data. Rows were not detected after initial processing of file.");
         }
         rows.forEach(arr -> {
            for (int i = 0 ; i < arr.length; i += 1) {
               if (!builderColumns.containsValue(i)) {
                  arr[i] = null;
               }
            }
         });
         List<String> cols = new ArrayList<>();
         cols.addAll(builderColumns.keySet());

         return Collections.singletonList(new RandomCsvRowStep(rows.toArray(new String[][]{}), cols));
      }

      /**
       * Defines mapping from columns to session variables.
       */
      public ColumnsBuilder columns() {
         return new ColumnsBuilder();
      }

      /**
       * Path to the CSV file that should be loaded.
       */
      public Builder file(String file) {
         this.file = file;
         return this;
      }

      /**
       * Skip lines starting with character '#'.
       */
      public Builder skipComments(boolean hasHeader) {
         this.skipComments = hasHeader;
         return this;
      }

      /**
       * Automatically unquote the columns.
       */
      public Builder removeQuotes(boolean isQuotesRemoved) {
         this.isQuotesRemoved = isQuotesRemoved;
         return this;
      }

      public class ColumnsBuilder extends PairBuilder.OfString{
         /**
          * Use 0-based column as the key and variable name as the value.
          */
         @Override
         public void accept(String position, String columnVar) {
            Integer pos = Integer.parseInt(position);
            maxSize = (maxSize > pos ? maxSize : pos);
            if ((builderColumns.put(columnVar, pos) != null)) {
               throw new BenchmarkDefinitionException("Duplicate item '" + columnVar + "' in randomCSVrow step!");
            }
         }
      }
   }
}
