package io.hyperfoil.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Stream;

import org.aesh.terminal.utils.ANSI;

import io.hyperfoil.core.util.Util;

public class Table<T> {
   private boolean boldHeader = true;
   private Function<T, String> rowPrefix;
   private Function<T, String> rowSuffix;
   private final List<String> titles = new ArrayList<>();
   private final List<Function<T, String>> functions = new ArrayList<>();
   private final List<Align> aligns = new ArrayList<>();

   public Table<T> boldHeader(boolean boldHeader) {
      this.boldHeader = boldHeader;
      return this;
   }

   public Table<T> rowPrefix(Function<T, String> rowPrefix) {
      if (this.rowPrefix != null) {
         throw new IllegalStateException("Row prefix already set.");
      }
      this.rowPrefix = rowPrefix;
      return this;
   }

   public Table<T> rowSuffix(Function<T, String> rowSuffix) {
      if (this.rowSuffix != null) {
         throw new IllegalStateException("Row suffix already set.");
      }
      this.rowSuffix = rowSuffix;
      return this;
   }

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

      ArrayList<String> prefixes = rowPrefix == null ? null : new ArrayList<>();
      ArrayList<String> suffixes = rowSuffix == null ? null : new ArrayList<>();
      ArrayList<String[]> values = new ArrayList<>();
      int[] width = titles.stream().mapToInt(Table::width).toArray();
      map.forEach((key, value) -> {
         AtomicBoolean first = new AtomicBoolean(true);
         value.forEach(item -> {
            if (rowPrefix != null) {
               prefixes.add(rowPrefix.apply(item));
            }
            if (rowSuffix != null) {
               suffixes.add(rowSuffix.apply(item));
            }
            String[] row = new String[functions.size() + 1];
            row[0] = first.compareAndSet(true, false) ? key : "";
            width[0] = Math.max(width[0], width(key));
            for (int i = 1; i < row.length; ++i) {
               row[i] = functions.get(i - 1).apply(item);
               if (row[i] == null) {
                  row[i] = "";
               }
               width[i] = Math.max(width[i], width(row[i]));
            }
            values.add(row);
         });
      });
      return print(titles, prefixes, values, suffixes, aligns, width);
   }

   public String print(Stream<T> stream) {
      ArrayList<String[]> values = new ArrayList<>();
      ArrayList<String> prefixes = rowPrefix == null ? null : new ArrayList<>();
      ArrayList<String> suffixes = rowSuffix == null ? null : new ArrayList<>();
      int[] width = titles.stream().mapToInt(Table::width).toArray();
      stream.forEach(item -> {
         if (rowPrefix != null) {
            prefixes.add(rowPrefix.apply(item));
         }
         if (rowSuffix != null) {
            suffixes.add(rowSuffix.apply(item));
         }
         String[] row = new String[functions.size()];
         for (int i = 0; i < row.length; ++i) {
            row[i] = functions.get(i).apply(item);
            if (row[i] == null) {
               row[i] = "";
            }
            width[i] = Math.max(width[i], width(row[i]));
         }
         values.add(row);
      });
      return print(titles, prefixes, values, suffixes, aligns, width);
   }

   private static int width(String str) {
      int width = 0;
      for (int i = 0; i < str.length(); ++i) {
         if (str.charAt(i) == '\u001b') {
            ++i;
            while (i < str.length()) {
               char c = str.charAt(i);
               if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z') break;
               ++i;
            }
         } else {
            ++width;
         }
      }
      return width;
   }

   private String print(List<String> titles, List<String> prefixes, List<String[]> values, List<String> suffixes, List<Align> aligns, int[] width) {
      StringBuilder sb = new StringBuilder();
      if (boldHeader) {
         sb.append(ANSI.BOLD);
      }
      for (int i = 0; i < titles.size() - 1; ++i) {
         String title = titles.get(i);
         sb.append(title);
         pad(sb, width[i] - width(title) + 2);
      }
      sb.append(titles.get(titles.size() - 1));
      if (boldHeader) {
         sb.append(ANSI.RESET);
      }
      sb.append('\n');
      int prefixLength = prefixes == null ? 0 : prefixes.stream().filter(Objects::nonNull).mapToInt(Table::width).max().orElse(0);
      int suffixLength = prefixes == null ? 0 : prefixes.stream().filter(Objects::nonNull).mapToInt(Table::width).max().orElse(0);
      int rowNumber = 0;
      for (String[] row : values) {
         String prefix = prefixes == null ? null : prefixes.get(rowNumber);
         if (prefix != null) {
            sb.append(prefix);
            pad(sb, prefixLength - width(prefix));
         }
         for (int i = 0; i < row.length; ++i) {
            Align align = aligns.get(i);
            if (align == Align.RIGHT) {
               pad(sb, width[i] - width(row[i]));
            }
            sb.append(row[i]);
            if (align == Align.LEFT) {
               pad(sb, width[i] - width(row[i]));
            }
            sb.append("  ");
         }
         String suffix = suffixes == null ? null : suffixes.get(rowNumber);
         if (suffix != null) {
            sb.append(suffix);
            pad(sb, suffixLength - width(suffix));
         }
         sb.append('\n');
         ++rowNumber;
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
