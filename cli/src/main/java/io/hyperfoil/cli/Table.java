package io.hyperfoil.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Stream;

import io.hyperfoil.core.util.Util;

public class Table<T> {
   private final List<String> titles = new ArrayList<>();
   private final List<Function<T, String>> functions = new ArrayList<>();
   private final List<Align> aligns = new ArrayList<>();

   public Table<T> column(String title, Function<T, String> func) {
      return column(title, func, Align.LEFT);
   }

   public Table<T> columnInt(String title, Function<T, Integer> func) {
      return column(title, value -> String.valueOf(func.apply(value)), Align.RIGHT);
   }

   public Table<T> columnLong(String title, Function<T, Long> func) {
      return column(title, value -> String.valueOf(func.apply(value)), Align.RIGHT);
   }

   public Table<T> columnNanos(String title, Function<T, Long> func) {
      return column(title, value -> Util.prettyPrintNanos(func.apply(value)), Align.RIGHT);
   }

   public Table<T> column(String title, Function<T, String> func, Align align) {
      titles.add(title);
      functions.add(func);
      aligns.add(align);
      return this;
   }

   public String print(String keyTitle, Map<String, Stream<T>> map) {
      ArrayList<String> titles = new ArrayList<>();
      titles.add(keyTitle);
      titles.addAll(this.titles);

      ArrayList<Align> aligns = new ArrayList<>();
      aligns.add(Align.LEFT);
      aligns.addAll(this.aligns);

      ArrayList<String[]> values = new ArrayList<>();
      int[] width = titles.stream().mapToInt(String::length).toArray();
      map.forEach((key, value) -> {
         AtomicBoolean first = new AtomicBoolean(true);
         value.forEach(item -> {
            String[] row = new String[functions.size() + 1];
            row[0] = first.compareAndSet(true, false) ? key : "";
            width[0] = Math.max(width[0], key.length());
            for (int i = 1; i < row.length; ++i) {
               row[i] = functions.get(i - 1).apply(item);
               if (row[i] == null) {
                  row[i] = "";
               }
               width[i] = Math.max(width[i], row[i].length());
            }
            values.add(row);
         });
      });
      return print(titles, values, aligns, width);
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
      return print(titles, values, aligns, width);
   }

   private static String print(List<String> titles, List<String[]> values, List<Align> aligns, int[] width) {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < titles.size() - 1; ++i) {
         String title = titles.get(i);
         sb.append(title);
         pad(sb, width[i] - title.length() + 2);
      }
      sb.append(titles.get(titles.size() - 1)).append('\n');
      for (String[] row : values) {
         for (int i = 0; i < row.length; ++i) {
            Align align = aligns.get(i);
            if (align == Align.RIGHT) {
               pad(sb, width[i] - row[i].length());
            }
            sb.append(row[i]);
            if (align == Align.LEFT) {
               pad(sb, width[i] - row[i].length());
            }
            sb.append("  ");
         }
         sb.append('\n');
      }
      return sb.toString();
   }

   private static void pad(StringBuilder sb, int n) {
      for (int i = 0; i < n; ++i) {
         sb.append(' ');
      }
   }

   public enum Align {
      LEFT,
      RIGHT
   }
}
