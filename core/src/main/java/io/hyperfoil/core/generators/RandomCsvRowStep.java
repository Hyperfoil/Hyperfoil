package io.hyperfoil.core.generators;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

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
   private final String[][] rows;
   private final ObjectAccess[] columnVars;

   public RandomCsvRowStep(String[][] rows, ObjectAccess[] columnVars) {
      this.rows = rows;
      this.columnVars = columnVars;
   }

   @Override
   public boolean invoke(Session session) {
      if (rows.length == 0) {
         throw new RuntimeException("No rows available - was the CSV file empty?");
      }
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
      private char separator = ',';
      private final List<String> builderColumns = new ArrayList<>();

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
            ArrayList<String[]> records = new ArrayList<>();
            String line;
            ArrayList<String> currentRecord = new ArrayList<>();
            StringBuilder sb = new StringBuilder();
            boolean quoted = false;
            boolean maybeClosingQuote = false;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
               if (!quoted && skipComments && line.stripLeading().startsWith("#")) {
                  continue;
               }
               for (int i = 0; i < line.length(); ++i) {
                  char c = line.charAt(i);
                  if (!quoted) {
                     if (c == separator) {
                        currentRecord.add(sb.toString());
                        sb.setLength(0);
                     } else if (c == '"') {
                        if (sb.length() != 0) {
                           throw new BenchmarkDefinitionException("The CSV file " + file + " is invalid; line " +
                                 lineNumber + " uses quote but it's quoting correctly (check preceding whitespaces?)");
                        }
                        quoted = true;
                     } else {
                        sb.append(c);
                     }
                  } else {
                     if (c == '"') {
                        if (maybeClosingQuote) {
                           sb.append(c); // quoted quote
                           maybeClosingQuote = false;
                        } else {
                           maybeClosingQuote = true;
                        }
                     } else if (maybeClosingQuote) {
                        quoted = false;
                        maybeClosingQuote = false;
                        if (c == separator) {
                           currentRecord.add(sb.toString());
                           sb.setLength(0);
                        } else {
                           throw new BenchmarkDefinitionException("The CSV file " + file + " is invalid; line " +
                                 lineNumber + " uses quote but it's quoting correctly (check characters after?)");
                        }
                     } else {
                        sb.append(c);
                     }
                  }
               }
               if (maybeClosingQuote) {
                  quoted = false;
                  maybeClosingQuote = false;
               }
               if (!quoted) {
                  currentRecord.add(sb.toString());
                  sb.setLength(0);
                  String[] arr = new String[srcIndex.length];
                  Arrays.setAll(arr, i -> srcIndex[i] < currentRecord.size() ? currentRecord.get(srcIndex[i]) : null);
                  records.add(arr);
               }
               ++lineNumber;
            }
            String[][] rows = records.toArray(new String[0][]);
            // We won't throw an error even if the CSV is empty - this can happen during edit in CLI when we expect
            // to reuse the data on server side.

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
       * Skip lines starting with character '#'. By default set to false.
       *
       * @param skipComments Skip?
       * @return Self.
       */
      public Builder skipComments(boolean skipComments) {
         this.skipComments = skipComments;
         return this;
      }

      /**
       * DEPRECATED. Quotes are removed automatically.
       *
       * @param removeQuotes Remove?
       * @return Self.
       * @deprecated
       */
      @Deprecated
      public Builder removeQuotes(@SuppressWarnings("unused") boolean removeQuotes) {
         return this;
      }

      /**
       * Set character used for column separation. By default it is comma (<code>,</code>).
       *
       * @param separator Separator character.
       * @return Self.
       */
      public Builder separator(char separator) {
         this.separator = separator;
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
