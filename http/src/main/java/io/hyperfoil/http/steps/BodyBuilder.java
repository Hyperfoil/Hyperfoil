package io.hyperfoil.http.steps;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.Locator;
import io.hyperfoil.api.session.Access;
import io.hyperfoil.core.generators.Pattern;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.core.util.ConstantBytesGenerator;
import io.hyperfoil.core.util.FromVarBytesGenerator;
import io.hyperfoil.core.util.Util;

/**
 * Allows building HTTP request body from session variables.
 */
public class BodyBuilder {
   private final HttpRequestStepBuilder parent;

   public BodyBuilder(HttpRequestStepBuilder parent) {
      this.parent = parent;
   }

   /**
    * Use variable content as request body.
    *
    * @param var Variable name.
    * @return Self.
    */
   public BodyBuilder fromVar(String var) {
      parent.body(() -> {
         Access access = SessionFactory.access(var);
         return new FromVarBytesGenerator(access);
      });
      return this;
   }

   /**
    * Pattern replacing <code>${sessionvar}</code> with variable contents in a string.
    *
    * @param pattern Pattern.
    * @return Self.
    */
   public BodyBuilder pattern(String pattern) {
      parent.body(() -> new Pattern(pattern, false).generator());
      return this;
   }

   /**
    * String sent as-is.
    *
    * @param text String.
    * @return Self.
    */
   public BodyBuilder text(String text) {
      parent.body(new ConstantBytesGenerator(text.getBytes(StandardCharsets.UTF_8)));
      return this;
   }

   /**
    * Build form as if we were sending the request using HTML form. This option automatically adds
    * <code>Content-Type: application/x-www-form-urlencoded</code> to the request headers.
    *
    * @return Builder.
    */
   public FormGenerator.Builder form() {
      FormGenerator.Builder builder = new FormGenerator.Builder();
      parent.headerAppender(new FormGenerator.ContentTypeWriter());
      parent.body(builder);
      return builder;
   }

   /**
    * Send contents of the file. Note that this method does NOT set content-type automatically.
    *
    * @param path Path to loaded file.
    * @return Self.
    */
   public BodyBuilder fromFile(String path) {
      parent.body(() -> {
         try (InputStream inputStream = Locator.current().benchmark().data().readFile(path)) {
            if (inputStream == null) {
               throw new BenchmarkDefinitionException("Cannot load file `" + path + "` for randomItem (not found).");
            }
            byte[] bytes = Util.toByteArray(inputStream);
            return new ConstantBytesGenerator(bytes);
         } catch (IOException e) {
            throw new BenchmarkDefinitionException("Cannot load file `" + path + "` for randomItem.", e);
         }
      });
      return this;
   }

   public HttpRequestStepBuilder endBody() {
      return parent;
   }

}
