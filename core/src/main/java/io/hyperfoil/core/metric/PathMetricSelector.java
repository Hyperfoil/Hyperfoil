package io.hyperfoil.core.metric;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.hyperfoil.api.config.ListBuilder;
import io.hyperfoil.api.config.Visitor;
import io.hyperfoil.function.SerializableFunction;

/**
 * Allows categorizing request statistics into metrics based on the request path. The expressions are evaluated
 * in the order as provided in the list.
 * Use one of: <ul>
 * <li><code>regexp -&gt; replacement</code>, e.g. <code>([^?]*)(\?.*)? -&gt; $1</code> to drop the query part.
 * <li><code>regexp</code> (don't do any replaces and use the full path), e.g. <code>.*.jpg</code>
 * <li><code>-&gt; name</code> (metric applied if none of the previous expressions match).
 * </ul>
 */
public class PathMetricSelector implements ListBuilder, MetricSelector {
   public List<SerializableFunction<String, String>> tests = new ArrayList<>();

   @Override
   public void nextItem(String item) {
      item = item.trim();
      int arrow = item.indexOf("->");
      if (arrow < 0) {
         Pattern pattern = Pattern.compile(item);
         tests.add(new SimpleMatch(pattern));
      } else if (arrow == 0) {
         String replacement = item.substring(2).trim();
         tests.add(new Fallback(replacement));
      } else {
         Pattern pattern = Pattern.compile(item.substring(0, arrow).trim());
         String replacement = item.substring(arrow + 2).trim();
         tests.add(new ReplaceMatch(pattern, replacement));
      }
   }

   @Override
   public String apply(String authority, String path) {
      String combined = authority != null ? authority + path : path;
      for (SerializableFunction<String, String> test : tests) {
         String result = test.apply(combined);
         if (result != null) {
            return result;
         }
      }
      return null;
   }

   private static class SimpleMatch implements SerializableFunction<String, String> {
      @Visitor.Invoke(method = "pattern")
      private final Pattern pattern;

      public SimpleMatch(Pattern pattern) {
         this.pattern = pattern;
      }

      public String pattern() {
         return pattern.pattern();
      }

      @Override
      public String apply(String path) {
         return pattern.matcher(path).matches() ? path : null;
      }
   }

   private static class Fallback implements SerializableFunction<String, String> {
      private final String metric;

      public Fallback(String metric) {
         this.metric = metric;
      }

      @Override
      public String apply(String path) {
         return metric;
      }
   }

   private static class ReplaceMatch implements SerializableFunction<String, String> {
      @Visitor.Invoke(method = "pattern")
      private final Pattern pattern;
      private final String replacement;

      public ReplaceMatch(Pattern pattern, String replacement) {
         this.pattern = pattern;
         this.replacement = replacement;
      }

      public String pattern() {
         return pattern.pattern();
      }

      @Override
      public String apply(String path) {
         Matcher matcher = pattern.matcher(path);
         if (matcher.matches()) {
            return matcher.replaceFirst(replacement);
         } else {
            return null;
         }
      }
   }
}
