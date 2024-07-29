package io.hyperfoil.core.generators;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.InitFromParam;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.config.Visitor;
import io.hyperfoil.api.connection.Connection;
import io.hyperfoil.api.processor.Transformer;
import io.hyperfoil.api.session.ReadAccess;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.function.SerializableBiConsumer;
import io.hyperfoil.function.SerializableBiFunction;
import io.hyperfoil.function.SerializableFunction;
import io.hyperfoil.impl.Util;
import io.netty.buffer.ByteBuf;

public class Pattern implements SerializableFunction<Session, String>, SerializableBiConsumer<Session, ByteBuf>, Transformer {
   private static final int VAR_LENGTH_ESTIMATE = 32;
   private static final String REPLACE = "replace";
   private final Component[] components;
   @Visitor.Ignore
   private int lengthEstimate;
   private final boolean urlEncode;

   public Pattern(String str, boolean urlEncode) {
      this(str, urlEncode, false);
   }

   public Pattern(String str, boolean urlEncode, boolean allowUnset) {
      this.urlEncode = urlEncode;
      List<Component> components = new ArrayList<>();
      int last = 0, lastSearch = 0;
      for (;;) {
         int openPar = str.indexOf("${", lastSearch);
         if (openPar < 0) {
            String substring = str.substring(last);
            if (!str.isEmpty()) {
               components.add(new StringComponent(substring.replaceAll("\\$\\$\\{", "\\${")));
               lengthEstimate += substring.length();
            }
            break;
         } else {
            if (openPar > 0 && str.charAt(openPar - 1) == '$') {
               // $${...} = escape syntax
               lastSearch = openPar + 1;
               continue;
            }
            String substring = str.substring(last, openPar);
            components.add(new StringComponent(substring.replaceAll("\\$\\$\\{", "\\${")));
            lengthEstimate += substring.length() + VAR_LENGTH_ESTIMATE;
            int closePar = str.indexOf("}", openPar);
            int colon = str.indexOf(":", openPar);
            if (colon >= 0 && colon < closePar) {
               String format = str.substring(openPar + 2, colon).trim();
               ReadAccess key = SessionFactory.readAccess(str.substring(colon + 1, closePar).trim());
               // TODO: we can't pre-allocate formatters here but we could cache them in the session
               // TODO: find a better place for this hack
               if (format.equalsIgnoreCase("urlencode")) {
                  if (urlEncode) {
                     throw new BenchmarkDefinitionException("It seems you're trying to URL-encode value twice.");
                  }
                  components.add(new VarComponent(key, allowUnset, Pattern::urlEncode));
                  // TODO: More efficient encoding/decoding: we're converting object to string, then to byte array and then allocating another string
               } else if (format.equalsIgnoreCase("base64encode")) {
                  components.add(new VarComponent(key, allowUnset,
                        s -> Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8))));
               } else if (format.equalsIgnoreCase("base64decode")) {
                  components.add(new VarComponent(key, allowUnset,
                        s -> new String(Base64.getDecoder().decode(s), StandardCharsets.UTF_8)));
               } else if (format.startsWith(REPLACE)) {
                  if (format.length() == REPLACE.length()) {
                     throw new BenchmarkDefinitionException(wrongReplaceSyntax(str, format));
                  }
                  char separator = format.charAt(REPLACE.length());
                  int regexpEnd = format.indexOf(separator, REPLACE.length() + 1);
                  int replacementEnd = format.indexOf(separator, regexpEnd + 1);
                  if (regexpEnd < 0 || replacementEnd < 0) {
                     throw new BenchmarkDefinitionException(wrongReplaceSyntax(str, format));
                  }
                  java.util.regex.Pattern regex = java.util.regex.Pattern
                        .compile(format.substring(REPLACE.length() + 1, regexpEnd));
                  String replacement = format.substring(regexpEnd + 1, replacementEnd);
                  boolean all = false;
                  if (format.length() > replacementEnd + 1) {
                     String flags = format.substring(replacementEnd + 1);
                     if ("g".equals(flags)) {
                        all = true;
                     } else {
                        throw new BenchmarkDefinitionException(
                              "Unknown flags '" + flags + "' in replace expression in '" + str + "'");
                     }
                  }
                  if (all) {
                     components.add(new VarComponent(key, allowUnset, value -> regex.matcher(value).replaceAll(replacement)));
                  } else {
                     components.add(new VarComponent(key, allowUnset, value -> regex.matcher(value).replaceFirst(replacement)));
                  }
               } else if (format.endsWith("d") || format.endsWith("o") || format.endsWith("x") || format.endsWith("X")) {
                  components.add(new FormatIntComponent(format, key));
               } else {
                  throw new IllegalArgumentException("Cannot use format string '" + format + "', only integers are supported");
               }
            } else if (closePar < 0) {
               throw new BenchmarkDefinitionException("Missing closing parentheses (}) in '" + str + "'");
            } else {
               ReadAccess key = SessionFactory.readAccess(str.substring(openPar + 2, closePar).trim());
               components.add(new VarComponent(key, allowUnset, urlEncode ? Pattern::urlEncode : null));
            }
            lastSearch = last = closePar + 1;
         }
      }
      this.components = components.toArray(new Component[0]);
   }

   private String wrongReplaceSyntax(String pattern, String expression) {
      return "Wrong replace syntax: use ${replace/regexp/replacement/flags:my-variable} " +
            "where '/' can be any character. The offending replace expression was '" +
            expression + "' in '" + pattern + "'.";
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

   public Generator generator() {
      return new Generator(this);
   }

   interface Component extends Serializable {
      void accept(Session session, StringBuilder sb);

      void accept(Session session, ByteBuf buf);
   }

   private static class Generator implements SerializableBiFunction<Session, Connection, ByteBuf> {
      private final Pattern pattern;

      private Generator(Pattern pattern) {
         this.pattern = pattern;
      }

      @Override
      public ByteBuf apply(Session session, Connection connection) {
         ByteBuf buffer = connection.context().alloc().buffer(pattern.lengthEstimate);
         pattern.accept(session, buffer);
         return buffer;
      }
   }

   private class StringComponent implements Component {
      private final String substring;
      @Visitor.Ignore
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
      private final ReadAccess key;

      FormatIntComponent(String format, ReadAccess key) {
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
      private final ReadAccess key;
      private final boolean allowUnset;
      private final SerializableFunction<String, String> transform;

      VarComponent(ReadAccess key, boolean allowUnset, SerializableFunction<String, String> transform) {
         this.key = key;
         this.allowUnset = allowUnset;
         this.transform = transform;
      }

      @Override
      public void accept(Session session, StringBuilder sb) {
         Session.Var var = key.getVar(session);
         if (!var.isSet()) {
            if (allowUnset) {
               sb.append("<not set>");
            } else {
               throw new IllegalArgumentException("Variable " + key + " is not set!");
            }
         } else {
            switch (var.type()) {
               case OBJECT:
                  String str = Util.prettyPrintObject(var.objectValue(session));
                  if (transform != null) {
                     str = transform.apply(str);
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
                     CharSequence str = o instanceof CharSequence ? (CharSequence) o : Util.prettyPrintObject(o);
                     if (transform != null) {
                        str = transform.apply(str.toString());
                     }
                     Util.string2byteBuf(str, buf);
                  } else {
                     Util.string2byteBuf("null", buf);
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

   /**
    * Use <a href="https://hyperfoil.io/docs/user-guide/benchmark/variables#string-interpolation">pattern</a> replacing session
    * variables.
    */
   @MetaInfServices(Transformer.Builder.class)
   @Name("pattern")
   public static class TransformerBuilder implements Transformer.Builder, InitFromParam<TransformerBuilder> {
      public String pattern;

      /**
       * Use <a href="https://hyperfoil.io/docs/user-guide/benchmark/variables#string-interpolation">pattern</a> replacing
       * session variables.
       *
       * @param param The pattern formatting string.
       * @return Self.
       */
      @Override
      public TransformerBuilder init(String param) {
         return pattern(param);
      }

      /**
       * Use <a href="https://hyperfoil.io/docs/user-guide/benchmark/variables#string-interpolation">pattern</a> replacing
       * session variables.
       *
       * @param pattern The pattern formatting string.
       * @return Self.
       */
      public TransformerBuilder pattern(String pattern) {
         this.pattern = pattern;
         return this;
      }

      @Override
      public Pattern build(boolean fragmented) {
         return new Pattern(pattern, false);
      }
   }
}
