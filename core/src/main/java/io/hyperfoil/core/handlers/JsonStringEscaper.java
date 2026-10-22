package io.hyperfoil.core.handlers;

import java.util.Collections;
import java.util.List;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.config.StepBuilder;
import io.hyperfoil.api.session.ObjectAccess;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.builders.BaseStepBuilder;
import io.hyperfoil.core.session.SessionFactory;

/**
 * Escapes a session variable to be safely embedded in JSON strings.
 * Converts special characters (newlines, quotes, backslashes, forward slashes) to their JSON-safe equivalents.
 */
public class JsonStringEscaper implements Step {
   private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

   private final ObjectAccess fromVar;
   private final ObjectAccess toVar;

   public JsonStringEscaper(ObjectAccess fromVar, ObjectAccess toVar) {
      this.fromVar = fromVar;
      this.toVar = toVar;
   }

   @Override
   public boolean invoke(Session session) {
      Object value = fromVar.getObject(session);
      if (value != null) {
         String escaped = escapeJsonString(value.toString());
         toVar.setObject(session, escaped);
      }
      return true;
   }

   private String escapeJsonString(String s) {
      if (s == null) {
         return null;
      }

      // Scan to find the exact index where escaping is first needed
      int firstEscapeIndex = -1;
      for (int i = 0; i < s.length(); i++) {
         char c = s.charAt(i);
         // Added '/' to prevent XSS issues when JSON is embedded in HTML
         if (c == '"' || c == '\\' || c == '/' || c == '\n' || c == '\r' || c == '\t' || c == '\b' || c == '\f' || c < 32) {
            firstEscapeIndex = i;
            break;
         }
      }

      // Optimistic scan: bypass StringBuilder allocation entirely if the string is already safe.
      if (firstEscapeIndex == -1) {
         return s;
      }

      StringBuilder sb = new StringBuilder(s.length() + 16);
      // Append the safe prefix all at once
      sb.append(s, 0, firstEscapeIndex);

      // Resume processing only from the escape character onwards
      for (int i = firstEscapeIndex; i < s.length(); i++) {
         char c = s.charAt(i);
         switch (c) {
            case '"':
               sb.append("\\\"");
               break;
            case '\\':
               sb.append("\\\\");
               break;
            case '/':
               sb.append("\\/");
               break;
            case '\b':
               sb.append("\\b");
               break;
            case '\f':
               sb.append("\\f");
               break;
            case '\n':
               sb.append("\\n");
               break;
            case '\r':
               sb.append("\\r");
               break;
            case '\t':
               sb.append("\\t");
               break;
            default:
               if (c < 32) {
                  // Bitwise hex encoding. Bypasses String.format() object allocation overhead.
                  sb.append("\\u00");
                  sb.append(HEX_CHARS[(c >> 4) & 0xF]);
                  sb.append(HEX_CHARS[c & 0xF]);
               } else {
                  sb.append(c);
               }
         }
      }
      return sb.toString();
   }

   /**
    * Builder for JsonStringEscaper
    */
   @MetaInfServices(StepBuilder.class)
   @Name("jsonStringEscaper")
   public static class Builder extends BaseStepBuilder<Builder> {
      private String fromVar;
      private String toVar;

      public Builder fromVar(String fromVar) {
         this.fromVar = fromVar;
         return this;
      }

      public Builder toVar(String toVar) {
         this.toVar = toVar;
         return this;
      }

      @Override
      public List<Step> build() {
         if (fromVar == null || fromVar.isEmpty()) {
            throw new BenchmarkDefinitionException("fromVar cannot be null or empty");
         }
         if (toVar == null || toVar.isEmpty()) {
            throw new BenchmarkDefinitionException("toVar cannot be null or empty");
         }
         return Collections.singletonList(
               new JsonStringEscaper(SessionFactory.objectAccess(fromVar), SessionFactory.objectAccess(toVar)));
      }
   }
}
