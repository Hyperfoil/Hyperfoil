package io.hyperfoil.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

public class Table<T> {
   private final List<String> titles = new ArrayList<>();
   private final List<Function<T, String>> functions = new ArrayList<>();

   public Table<T> column(String title, Function<T, String> func) {
      titles.add(title);
      functions.add(func);
      return this;
   }

   public String print(Stream<T> stream) {
      ArrayList<String[]> values = new ArrayList<>();
      int[] width = titles.stream().mapToInt(String::length).toArray();
      stream.forEach(item -> {
         String[] row = new String[functions.size()];
         for (int i = 0; i < row.length; ++i) {
            row[i] = functions.get(i).apply(item);
            if (row[i] == null) {
               row[i] = "";
            }
            width[i] = Math.max(width[i], row[i].length());
         }
         values.add(row);
      });
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < titles.size() - 1; ++i) {
         String title = titles.get(i);
         sb.append(title);
         pad(sb, width[i] - title.length() + 2);
      }
      sb.append(titles.get(titles.size() - 1)).append('\n');
      for (String[] row : values) {
         for (int i = 0; i < row.length - 1; ++i) {
            sb.append(row[i]);
            pad(sb, width[i] - row[i].length() + 2);
         }
         sb.append(row[row.length - 1]).append('\n');
      }
      return sb.toString();
   }

   private void pad(StringBuilder sb, int n) {
      for (int i = 0; i < n; ++i) {
         sb.append(' ');
      }
   }
}
