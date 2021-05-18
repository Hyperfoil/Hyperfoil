package io.hyperfoil.core.generators;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.Locator;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.config.PairBuilder;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.config.StepBuilder;
import io.hyperfoil.api.session.ObjectAccess;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.builders.BaseStepBuilder;
import io.hyperfoil.core.session.SessionFactory;

/**
 * A class that will initialise, build and randomly select a single row of data.
 * The row is exposed as columns.
 */
public class RandomCsvRowStep implements Step {
   private String[][] rows;
   private final ObjectAccess[] columnVars;

   public RandomCsvRowStep(String[][] rows, ObjectAccess[] columnVars) {
      this.rows = rows;
      this.columnVars = columnVars;
   }

   @Override
   public boolean invoke(Session session) {
      // columns provided by csv
      ThreadLocalRandom random = ThreadLocalRandom.current();
      String[] row = rows[random.nextInt(rows.length)];
      for (int i = 0, j = 0; i < columnVars.length; i += 1) {
         columnVars[j++].setObject(session, row[i]);
      }
      return true;
   }

   /**
    * Stores random row from a CSV-formatted file to variables.
    */
   @MetaInfServices(StepBuilder.class)
   @Name("randomCsvRow")
   public static class Builder extends BaseStepBuilder<Builder> {
      private String file;
      private boolean skipComments;
      private boolean removeQuotes;
      private List<String> builderColumns = new ArrayList<>();

      @Override
      public List<Step> build() {
         int[] srcIndex = new int[(int) builderColumns.stream().filter(Objects::nonNull).count()];
         int next = 0;
         for (int i = 0; i < builderColumns.size(); ++i) {
            if (builderColumns.get(i) != null) {
               srcIndex[next++] = i;
            }
         }
         assert next == srcIndex.length;

         try (InputStream inputStream = Locator.current().benchmark().data().readFile(file)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            Stream<String> lines = reader.lines();
            if (skipComments) {
               lines = lines.filter(line -> !line.trim().startsWith("#"));
            }
            String[][] rows = lines.map(line -> {
               if (removeQuotes) {
                  line = line.replaceAll("\"", "");
               }
               String[] cols = line.split(",");
               String[] arr = new String[srcIndex.length];
               for (int i = 0; i < arr.length; ++i) {
                  arr[i] = cols[srcIndex[i]];
               }
               return arr;
            }).toArray(String[][]::new);
            if (rows.length == 0) {
               throw new BenchmarkDefinitionException("Missing CSV row data. Rows were not detected after initial processing of file.");
            }

            ObjectAccess[] columnVars = builderColumns.stream().filter(Objects::nonNull).map(SessionFactory::objectAccess).toArray(ObjectAccess[]::new);
            return Collections.singletonList(new RandomCsvRowStep(rows, columnVars));
         } catch (IOException ioe) {
            throw new BenchmarkDefinitionException("Failed to read file " + file, ioe);
         }
      }

      /**
       * Defines mapping from columns to session variables.
       *
       * @return Builder.
       */
      public ColumnsBuilder columns() {
         return new ColumnsBuilder();
      }

      /**
       * Path to the CSV file that should be loaded.
       *
       * @param file Path to file.
       * @return Self.
       */
      public Builder file(String file) {
         this.file = file;
         return this;
      }

      /**
       * Skip lines starting with character '#'.
       *
       * @param skipComments Skip?
       * @return Self.
       */
      public Builder skipComments(boolean skipComments) {
         this.skipComments = skipComments;
         return this;
      }

      /**
       * Automatically unquote the columns.
       *
       * @param removeQuotes Remove?
       * @return Self.
       */
      public Builder removeQuotes(boolean removeQuotes) {
         this.removeQuotes = removeQuotes;
         return this;
      }

      public class ColumnsBuilder extends PairBuilder.OfString {
         /**
          * Use 0-based column as the key and variable name as the value.
          *
          * @param position  0-based column number.
          * @param columnVar Variable name.
          */
         @Override
         public void accept(String position, String columnVar) {
            int pos = Integer.parseInt(position);
            if (pos < 0) {
               throw new BenchmarkDefinitionException("Negative column index is not supported.");
            }
            while (pos >= builderColumns.size()) {
               builderColumns.add(null);
            }
            String prev = builderColumns.set(pos, columnVar);
            if (prev != null) {
               throw new BenchmarkDefinitionException("Column " + pos + " is already mapped to '" + prev + "', don't map to '" + columnVar + "'");
            }
         }
      }
   }
}
