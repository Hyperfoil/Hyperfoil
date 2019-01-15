package io.hyperfoil.core.generators;

import java.util.ArrayList;
import java.util.List;

import io.hyperfoil.api.session.Session;
import io.hyperfoil.function.SerializableBiConsumer;
import io.hyperfoil.function.SerializableFunction;

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
               components.add((s, sb) -> sb.append(substring));
               lengthEstimate += substring.length();
            }
            break;
         } else {
            String substring = str.substring(last, openPar);
            components.add((s, sb) -> sb.append(substring));
            lengthEstimate += substring.length() + VAR_LENGTH_ESTIMATE;
            int closePar = str.indexOf("}", openPar);
            int colon = str.indexOf(":", openPar);
            if (colon >= 0 && colon < closePar) {
               String format = str.substring(openPar + 2, colon).trim();
               String key = str.substring(colon + 1, closePar).trim();
               // TODO: we can't pre-allocate formatters here but we could cache them in the session
               if (format.endsWith("d") || format.endsWith("o") || format.endsWith("x") || format.endsWith("X")) {
                  components.add((s, sb) -> sb.append(String.format(format, s.getInt(key))));
               } else {
                  throw new IllegalArgumentException("Cannot use format string '" + format + "', only integers are supported");
               }
            } else {
               String key = str.substring(openPar + 2, closePar).trim();
               components.add((s, sb) -> {
                  Session.Var var = s.getVar(key);
                  if (!var.isSet()) {
                     throw new IllegalArgumentException("Variable " + key + " is not set!");
                  } else {
                     switch (var.type()) {
                        case OBJECT:
                           sb.append(var.objectValue());
                           break;
                        case INTEGER:
                           sb.append(var.intValue());
                           break;
                        default:
                           throw new IllegalArgumentException("Unknown var type: " + var);
                     }
                  }
               });
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
         c.accept(session, sb);
      }
      return sb.toString();
   }

   interface Component extends SerializableBiConsumer<Session, StringBuilder> {}
}
