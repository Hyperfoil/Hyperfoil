package io.hyperfoil.core.generators;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.processor.Transformer;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.core.util.Util;
import io.hyperfoil.function.SerializableBiConsumer;
import io.hyperfoil.function.SerializableFunction;
import io.netty.buffer.ByteBuf;

public class Pattern implements SerializableFunction<Session, String>, SerializableBiConsumer<Session, ByteBuf>, Transformer {
   private static final int VAR_LENGTH_ESTIMATE = 32;
   private final Component[] components;
   private int lengthEstimate;
   private final boolean urlEncode;

   public Pattern(String str, boolean urlEncode) {
      this.urlEncode = urlEncode;
      List<Component> components = new ArrayList<>();
      int last = 0;
      for (; ; ) {
         int openPar = str.indexOf("${", last);
         if (openPar < 0) {
            String substring = str.substring(last);
            if (!str.isEmpty()) {
               components.add(new StringComponent(substring));
               lengthEstimate += substring.length();
            }
            break;
         } else {
            String substring = str.substring(last, openPar);
            components.add(new StringComponent(substring));
            lengthEstimate += substring.length() + VAR_LENGTH_ESTIMATE;
            int closePar = str.indexOf("}", openPar);
            int colon = str.indexOf(":", openPar);
            if (colon >= 0 && colon < closePar) {
               String format = str.substring(openPar + 2, colon).trim();
               Access key = SessionFactory.access(str.substring(colon + 1, closePar).trim());
               // TODO: we can't pre-allocate formatters here but we could cache them in the session
               // TODO: find a better place for this hack
               if (format.equalsIgnoreCase("urlencode")) {
                  if (urlEncode) {
                     throw new BenchmarkDefinitionException("It seems you're trying to URL-encode value twice.");
                  }
                  components.add(new VarComponent(key, true));
               } else if (format.endsWith("d") || format.endsWith("o") || format.endsWith("x") || format.endsWith("X")) {
                  components.add(new FormatIntComponent(format, key));
               } else {
                  throw new IllegalArgumentException("Cannot use format string '" + format + "', only integers are supported");
               }
            } else {
               Access key = SessionFactory.access(str.substring(openPar + 2, closePar).trim());
               components.add(new VarComponent(key, urlEncode));
            }
            last = closePar + 1;
         }
      }
      this.components = components.toArray(new Component[0]);
   }

   private static String urlEncode(String string) {
      try {
         return URLEncoder.encode(string, StandardCharsets.UTF_8.name());
      } catch (UnsupportedEncodingException e) {
         throw new IllegalArgumentException(e);
      }
   }

   @Override
   public String apply(Session session) {
      if (components.length == 1 && components[0] instanceof StringComponent) {
         return ((StringComponent) components[0]).substring;
      }
      StringBuilder sb = new StringBuilder(lengthEstimate);
      for (Component c : components) {
         c.accept(session, sb);
      }
      return sb.toString();
   }

   @Override
   public void accept(Session session, ByteBuf byteBuf) {
      for (Component c : components) {
         c.accept(session, byteBuf);
      }
   }

   @Override
   public void transform(Session session, ByteBuf in, int offset, int length, boolean lastFragment, ByteBuf out) {
      if (lastFragment) {
         accept(session, out);
      }
   }

   interface Component extends Serializable {
      void accept(Session session, StringBuilder sb);

      void accept(Session session, ByteBuf buf);
   }

   private class StringComponent implements Component {
      private final String substring;
      private final byte[] bytes;

      StringComponent(String substring) {
         if (urlEncode) {
            substring = urlEncode(substring);
         }
         this.substring = substring;
         this.bytes = substring.getBytes(StandardCharsets.UTF_8);
      }

      @Override
      public void accept(Session s, StringBuilder sb) {
         sb.append(substring);
      }

      @Override
      public void accept(Session session, ByteBuf buf) {
         buf.writeBytes(bytes);
      }
   }

   private class FormatIntComponent implements Component {
      private final String format;
      private final Access key;

      FormatIntComponent(String format, Access key) {
         this.format = format;
         this.key = key;
      }

      private String string(Session session) {
         return String.format(this.format, key.getInt(session));
      }

      @Override
      public void accept(Session s, StringBuilder sb) {
         String str = string(s);
         sb.append(urlEncode ? urlEncode(str) : str);
      }

      @Override
      public void accept(Session session, ByteBuf buf) {
         String str = string(session);
         if (urlEncode) {
            Util.urlEncode(str, buf);
         } else {
            Util.string2byteBuf(str, buf);
         }
      }
   }

   private static class VarComponent implements Component {
      private final Access key;
      private final boolean urlEncode;

      VarComponent(Access key, boolean urlEncode) {
         this.key = key;
         this.urlEncode = urlEncode;
      }

      @Override
      public void accept(Session session, StringBuilder sb) {
         Session.Var var = key.getVar(session);
         if (!var.isSet()) {
            throw new IllegalArgumentException("Variable " + key + " is not set!");
         } else {
            switch (var.type()) {
               case OBJECT:
                  String str = String.valueOf(var.objectValue(session));
                  if (urlEncode) {
                     str = urlEncode(str);
                  }
                  sb.append(str);
                  break;
               case INTEGER:
                  sb.append(var.intValue(session));
                  break;
               default:
                  throw new IllegalArgumentException("Unknown var type: " + var);
            }
         }
      }

      @Override
      public void accept(Session session, ByteBuf buf) {
         Session.Var var = key.getVar(session);
         if (!var.isSet()) {
            throw new IllegalArgumentException("Variable " + key + " is not set!");
         } else {
            switch (var.type()) {
               case OBJECT:
                  Object o = var.objectValue(session);
                  if (o != null) {
                     if (urlEncode) {
                        Util.urlEncode(o.toString(), buf);
                     } else {
                        Util.string2byteBuf(o.toString(), buf);
                     }
                  }
                  break;
               case INTEGER:
                  Util.intAsText2byteBuf(var.intValue(session), buf);
                  break;
               default:
                  throw new IllegalArgumentException("Unknown var type: " + var);
            }
         }
      }
   }
}
