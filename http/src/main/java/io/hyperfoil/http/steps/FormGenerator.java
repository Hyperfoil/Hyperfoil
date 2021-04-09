package io.hyperfoil.http.steps;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.MappingListBuilder;
import io.hyperfoil.api.connection.Connection;
import io.hyperfoil.http.api.HttpRequestWriter;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.generators.Pattern;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.core.util.Util;
import io.hyperfoil.function.SerializableBiConsumer;
import io.hyperfoil.function.SerializableBiFunction;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpHeaderNames;

public class FormGenerator implements SerializableBiFunction<Session, Connection, ByteBuf> {
   private static final String APPLICATION_X_WWW_FORM_URLENCODED = "application/x-www-form-urlencoded";

   private final SerializableBiConsumer<Session, ByteBuf>[] inputs;

   private FormGenerator(SerializableBiConsumer<Session, ByteBuf>[] inputs) {
      this.inputs = inputs;
   }

   @Override
   public ByteBuf apply(Session session, Connection connection) {
      if (inputs.length == 0) {
         return Unpooled.EMPTY_BUFFER;
      }
      ByteBuf buffer = connection.context().alloc().buffer();
      inputs[0].accept(session, buffer);
      for (int i = 1; i < inputs.length; ++i) {
         buffer.ensureWritable(1);
         buffer.writeByte('&');
         inputs[i].accept(session, buffer);
      }
      return buffer;
   }

   /**
    * Build an URL-encoded HTML form body.
    */
   // Note: we cannot implement both a PairBuilder and MappingListBuilder at the same time
   public static class Builder implements HttpRequestStepBuilder.BodyGeneratorBuilder, MappingListBuilder<InputBuilder> {
      private final ArrayList<InputBuilder> inputs = new ArrayList<>();

      /**
       * Add input pair described in the mapping.
       *
       * @return Builder.
       */
      @Override
      public InputBuilder addItem() {
         InputBuilder input = new InputBuilder();
         inputs.add(input);
         return input;
      }

      @SuppressWarnings("unchecked")
      @Override
      public SerializableBiFunction<Session, Connection, ByteBuf> build() {
         return new FormGenerator(inputs.stream().map(InputBuilder::build).toArray(SerializableBiConsumer[]::new));
      }
   }

   /**
    * Form element (e.g. as if coming from an INPUT field).
    */
   public static class InputBuilder {
      private String name;
      private String value;
      private String fromVar;
      private String pattern;

      public SerializableBiConsumer<Session, ByteBuf> build() {
         if (value != null && fromVar != null && pattern != null) {
            throw new BenchmarkDefinitionException("Form input: Must set only one of 'value', 'var', 'pattern'");
         } else if (value == null && fromVar == null && pattern == null) {
            throw new BenchmarkDefinitionException("Form input: Must set one of 'value' or 'var' or 'pattern'");
         } else if (name == null) {
            throw new BenchmarkDefinitionException("Form input: 'name' must be set.");
         }
         try {
            byte[] nameBytes = URLEncoder.encode(name, StandardCharsets.UTF_8.name()).getBytes(StandardCharsets.UTF_8);
            if (value != null) {
               byte[] valueBytes = URLEncoder.encode(value, StandardCharsets.UTF_8.name()).getBytes(StandardCharsets.UTF_8);
               return new ConstantInput(nameBytes, valueBytes);
            } else if (fromVar != null) {
               Access access = SessionFactory.access(fromVar);
               return new VariableInput(nameBytes, access);
            } else {
               Pattern pattern = new Pattern(this.pattern, true);
               return new PatternInput(nameBytes, pattern);
            }
         } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
         }
      }

      /**
       * Input field name.
       *
       * @param name Input name.
       * @return Self.
       */
      public InputBuilder name(String name) {
         this.name = name;
         return this;
      }

      /**
       * Input field value (verbatim).
       *
       * @param value Input value.
       * @return Self.
       */
      public InputBuilder value(String value) {
         this.value = value;
         return this;
      }

      /**
       * Input field value from session variable.
       *
       * @param var Variable name.
       * @return Self.
       */
      public InputBuilder fromVar(String var) {
         this.fromVar = var;
         return this;
      }

      /**
       * Input field value replacing session variables in a
       * <a href="https://hyperfoil.io/userguide/benchmark/variables.html#string-interpolation">pattern</a>,
       * e.g. <code>foo${myvariable}var</code>
       *
       * @param pattern Template pattern.
       * @return Self.
       */
      public InputBuilder pattern(String pattern) {
         this.pattern = pattern;
         return this;
      }

      private static class ConstantInput implements SerializableBiConsumer<Session, ByteBuf> {
         private final byte[] name;
         private final byte[] value;

         public ConstantInput(byte[] name, byte[] value) {
            this.name = name;
            this.value = value;
         }

         @Override
         public void accept(Session session, ByteBuf buf) {
            buf.writeBytes(name).writeByte('=').writeBytes(value);
         }
      }

      private static class VariableInput implements SerializableBiConsumer<Session, ByteBuf> {
         private final byte[] name;
         private final Access fromVar;

         public VariableInput(byte[] name, Access fromVar) {
            this.name = name;
            this.fromVar = fromVar;
         }

         @Override
         public void accept(Session session, ByteBuf buf) {
            buf.writeBytes(name).writeByte('=');
            Session.Var var = fromVar.getVar(session);
            if (!var.isSet()) {
               throw new IllegalStateException("Variable " + fromVar + " was not set yet!");
            }
            if (var.type() == Session.VarType.INTEGER) {
               Util.intAsText2byteBuf(var.intValue(session), buf);
            } else if (var.type() == Session.VarType.OBJECT) {
               Object o = var.objectValue(session);
               if (o == null) {
                  // keep it empty
               } else if (o instanceof byte[]) {
                  buf.writeBytes((byte[]) o);
               } else {
                  Util.urlEncode(o.toString(), buf);
               }
            } else {
               throw new IllegalStateException();
            }
         }
      }

      private static class PatternInput implements SerializableBiConsumer<Session, ByteBuf> {
         private final byte[] name;
         private final Pattern pattern;

         public PatternInput(byte[] name, Pattern pattern) {
            this.name = name;
            this.pattern = pattern;
         }

         @Override
         public void accept(Session session, ByteBuf buf) {
            buf.writeBytes(name).writeByte('=');
            pattern.accept(session, buf);
         }
      }
   }

   public static class ContentTypeWriter implements SerializableBiConsumer<Session, HttpRequestWriter> {
      @Override
      public void accept(Session session, HttpRequestWriter writer) {
         writer.putHeader(HttpHeaderNames.CONTENT_TYPE, APPLICATION_X_WWW_FORM_URLENCODED);
      }
   }
}
