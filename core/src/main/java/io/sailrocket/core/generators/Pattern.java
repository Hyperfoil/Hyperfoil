package io.sailrocket.core.generators;

import java.util.ArrayList;
import java.util.List;

import io.sailrocket.api.session.Session;
import io.sailrocket.function.SerializableFunction;

public class Pattern implements SerializableFunction<Session,String> {
   private static final int VAR_LENGTH_ESTIMATE = 32;
   private final Component[] components;
   private int lengthEstimate;

   public Pattern(String str) {
      List<Component> components = new ArrayList<>();
      int last = 0;
      for (;;) {
         int openPar = str.indexOf("${", last);
         if (openPar < 0) {
            String substring = str.substring(last);
            if (!str.isEmpty()) {
               components.add(s -> substring);
               lengthEstimate += substring.length();
            }
            break;
         } else {
            String substring = str.substring(last, openPar);
            components.add(s -> substring);
            lengthEstimate += substring.length() + VAR_LENGTH_ESTIMATE;
            int closePar = str.indexOf("}", openPar);
            int colon = str.indexOf(":", openPar);
            if (colon >= 0 && colon < closePar) {
               String format = str.substring(openPar + 2, colon).trim();
               String var = str.substring(colon + 1, closePar).trim();
               // TODO: we can't pre-allocate formatters here but we could cache them in the session
               if (format.endsWith("d") || format.endsWith("o") || format.endsWith("x") || format.endsWith("X")) {
                  components.add(s -> String.format(format, s.getInt(var)));
               } else {
                  throw new IllegalArgumentException("Cannot use format string '" + format + "', only integers are supported");
               }
            } else {
               String var = str.substring(openPar + 2, closePar).trim();
               components.add(s -> s.getAsString(var));
            }
            last = closePar + 1;
         }
      }
      this.components = components.toArray(new Component[0]);
   }

   @Override
   public String apply(Session session) {
      StringBuilder sb = new StringBuilder(lengthEstimate);
      for (Component c : components) {
         sb.append(c.apply(session));
      }
      return sb.toString();
   }

   interface Component extends SerializableFunction<Session, CharSequence> {}
}
