package io.hyperfoil.cli;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.aesh.terminal.utils.ANSI;

import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.core.util.Util;

public class Table<T> {
   private boolean boldHeader = true;
   private Function<T, String> rowPrefix;
   private Function<T, String> rowSuffix;
   private final List<String> titles;
   private final List<Function<T, String>> functions;
   private final List<Align> aligns;
   private int idColumns = 1;

   public Table() {
      titles = new ArrayList<>();
      functions = new ArrayList<>();
      aligns = new ArrayList<>();
   }

   public Table(Table<T> other) {
      boldHeader = other.boldHeader;
      idColumns = other.idColumns;
      rowPrefix = other.rowPrefix;
      rowSuffix = other.rowSuffix;
      titles = new ArrayList<>(other.titles);
      functions = new ArrayList<>(other.functions);
      aligns = new ArrayList<>(other.aligns);
   }

   public Table<T> boldHeader(boolean boldHeader) {
      this.boldHeader = boldHeader;
      return this;
   }

   public Table<T> idColumns(int columns) {
      this.idColumns = columns;
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

   public int print(HyperfoilCommandInvocation invocation, String keyTitle, Map<String, Stream<T>> map) {
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
            if (rowPrefix != null && prefixes != null) {
               prefixes.add(rowPrefix.apply(item));
            }
            if (rowSuffix != null && suffixes != null) {
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
      return print(invocation, titles, prefixes, values, suffixes, aligns, width);
   }

   public int print(HyperfoilCommandInvocation invocation, Stream<T> stream) {
      ArrayList<String[]> values = new ArrayList<>();
      ArrayList<String> prefixes = rowPrefix == null ? null : new ArrayList<>();
      ArrayList<String> suffixes = rowSuffix == null ? null : new ArrayList<>();
      int[] width = titles.stream().mapToInt(Table::width).toArray();
      stream.forEach(item -> {
         if (rowPrefix != null && prefixes != null) {
            prefixes.add(rowPrefix.apply(item));
         }
         if (rowSuffix != null && suffixes != null) {
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
      return print(invocation, titles, prefixes, values, suffixes, aligns, width);
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

   private int print(HyperfoilCommandInvocation invocation, List<String> titles, List<String> prefixes, List<String[]> values, List<String> suffixes, List<Align> aligns, int[] width) {
      StringBuilder sb = new StringBuilder();
      int lines = 0;
      if (boldHeader) {
         sb.append(ANSI.BOLD);
      }
      int prefixLength = prefixes == null ? 0 : prefixes.stream().filter(Objects::nonNull).mapToInt(Table::width).max().orElse(0);
      int suffixLength = prefixes == null ? 0 : prefixes.stream().filter(Objects::nonNull).mapToInt(Table::width).max().orElse(0);
      int totalWidth = IntStream.of(width).map(w -> w + 2).sum() - 2;
      int maxWidth = invocation.getShell().size().getWidth() - prefixLength - suffixLength;
      boolean multiline = totalWidth > maxWidth;
      int idsWidth = IntStream.of(width).limit(idColumns).map(w -> w + 2).sum();
      int stride = width.length - idColumns;
      if (multiline) {
         // let's assume that title columns and one column will always fit
         int[] newWidth = Arrays.copyOf(width, width.length);
         do {
            int currentStride = stride;
            for (int i = idColumns; i < idColumns + stride; ++i) {
               newWidth[i] = IntStream.iterate(i, x -> x < width.length, x -> x + currentStride)
                     .map(j -> width[j]).max().orElse(width[i]);
            }
            int lineWidth = idsWidth + IntStream.of(newWidth).skip(idColumns).limit(stride).map(w -> w + 2).sum() - 2;
            if (lineWidth > maxWidth) {
               System.arraycopy(width, idColumns, newWidth, idColumns, width.length - idColumns);
               --stride;
            } else {
               break;
            }
         } while (stride > 1);
         System.arraycopy(newWidth, idColumns, width, idColumns, stride);
         //noinspection ManualArrayCopy
         for (int i = idColumns + stride; i < width.length; ++i) {
            width[i] = width[i - stride];
         }
      }
      for (int i = 0; i < idColumns; ++i) {
         String title = titles.get(i);
         sb.append(title);
         pad(sb, width[i] - width(title) + 2, ' ');
      }
      for (int i = idColumns; i < width.length; i += stride) {
         for (int j = 0; j < stride - 1 && i + j < width.length; ++j) {
            String title = titles.get(i + j);
            sb.append(title);
            pad(sb, width[i + j] - width(title) + 2, ' ');
         }
         if (i + stride - 1 < width.length) {
            sb.append(titles.get(i + stride - 1));
         }
         if (i + stride < width.length) {
            sb.append('\n');
            pad(sb, idsWidth, ' ');
            ++lines;
         }
      }
      if (boldHeader) {
         sb.append(ANSI.RESET);
      }
      if (multiline) {
         sb.append('\n');
         pad(sb, maxWidth, '-');
         ++lines;
      }
      sb.append('\n');
      ++lines;
      int rowNumber = 0;
      for (String[] row : values) {
         String prefix = prefixes == null ? null : prefixes.get(rowNumber);
         String suffix = suffixes == null ? null : suffixes.get(rowNumber);
         if (prefix != null) {
            sb.append(prefix);
            pad(sb, prefixLength - width(prefix), ' ');
         }
         for (int i = 0; i < idColumns; ++i) {
            printAligned(sb, row, width, aligns, i);
            sb.append("  ");
         }
         for (int i = idColumns; i < width.length; i += stride) {
            for (int j = 0; j < stride - 1 && i + j < width.length; ++j) {
               printAligned(sb, row, width, aligns, i + j);
               sb.append("  ");
            }
            if (i + stride - 1 < width.length) {
               printAligned(sb, row, width, aligns, i + stride - 1);
            }
            if (i + stride < width.length) {
               if (suffix != null) {
                  sb.append(suffix);
                  pad(sb, suffixLength - width(suffix), ' ');
               }
               sb.append('\n');
               if (prefix != null) {
                  sb.append(prefix);
                  pad(sb, prefixLength - width(prefix), ' ');
               }
               pad(sb, idsWidth, ' ');
               ++lines;
            }
         }
         if (suffix != null) {
            sb.append(suffix);
            pad(sb, suffixLength - width(suffix), ' ');
         }
         if (multiline) {
            sb.append('\n');
            pad(sb, maxWidth, '-');
            ++lines;
         }
         sb.append('\n');
         ++rowNumber;
         ++lines;
      }
      invocation.print(sb.toString());
      return lines;
   }

   private void printAligned(StringBuilder sb, String[] row, int[] width, List<Align> aligns, int i) {
      Align align = aligns.get(i);
      if (align == Align.RIGHT) {
         pad(sb, width[i] - width(row[i]), ' ');
      }
      sb.append(row[i]);
      if (align == Align.LEFT) {
         pad(sb, width[i] - width(row[i]), ' ');
      }
   }

   private static void pad(StringBuilder sb, int n, char c) {
      //noinspection StringRepeatCanBeUsed
      for (int i = 0; i < n; ++i) {
         sb.append(c);
      }
   }

   public enum Align {
      LEFT,
      RIGHT
   }
}
