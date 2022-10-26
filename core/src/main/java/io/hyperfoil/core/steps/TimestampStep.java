package io.hyperfoil.core.steps;

import io.hyperfoil.api.config.InitFromParam;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.config.StepBuilder;
import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.session.ObjectAccess;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.builders.BaseStepBuilder;
import io.hyperfoil.core.session.SessionFactory;
import io.hyperfoil.function.SerializableSupplier;

import org.kohsuke.MetaInfServices;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * This class implements a {@link Step} that bumps the current time in milliseconds as {@link String} to a variable.
 */
public class TimestampStep implements Step, ResourceUtilizer {
   private final ObjectAccess toVar;
   private final Session.ResourceKey<FormatterResource> formatterKey;
   private final SerializableSupplier<FormatterResource> formatterSupplier;

   public TimestampStep(ObjectAccess toVar, Session.ResourceKey<FormatterResource> formatterKey, SerializableSupplier<FormatterResource> formatterSupplier) {
      this.toVar = toVar;
      this.formatterKey = formatterKey;
      this.formatterSupplier = formatterSupplier;
   }

   @Override
   public boolean invoke(Session session) {
      String timestamp;
      if (formatterKey != null) {
         timestamp = session.getResource(formatterKey).format.format(new Date());
      } else {
         timestamp = String.valueOf(System.currentTimeMillis());
      }
      toVar.setObject(session, timestamp);
      return true;
   }

   @Override
   public void reserve(Session session) {
      if (formatterKey != null) {
         session.declareResource(formatterKey, formatterSupplier);
      }
   }

   private static class FormatterResource implements Session.Resource {
      private final SimpleDateFormat format;

      public FormatterResource(String pattern, String localeCountry) {
         Locale locale;
         if (localeCountry == null || localeCountry.isBlank()) {
            locale = Locale.US; // we need a deterministic choice
         } else {
            locale = Stream.of(Locale.getAvailableLocales())
                  .filter(l -> localeCountry.equals(l.getCountry())).findFirst()
                  .orElseThrow(() -> new IllegalArgumentException("No locale with 2-letter country code '" + localeCountry + "' available."));
         }
         this.format = new SimpleDateFormat(pattern, locale);
      }
   }

   /**
    * Stores the current time in milliseconds as string to a session variable.
    */
   @MetaInfServices(StepBuilder.class)
   @Name("timestamp")
   public static class Builder extends BaseStepBuilder<Builder> implements InitFromParam<Builder> {
      private String toVar;
      private String pattern;
      private String localeCountry;

      /**
       * @param param Variable name.
       * @return Self.
       */
      @Override
      public Builder init(String param) {
         return toVar(param);
      }

      /**
       * Target variable name.
       *
       * @param toVar Variable name.
       * @return Self.
       */
      public Builder toVar(String toVar) {
         this.toVar = toVar;
         return this;
      }

      /**
       * Format the timestamp using <code>SimpleDateFormat</code> pattern.
       *
       * @param pattern Pattern for formatting.
       * @return Self.
       */
      public Builder pattern(String pattern) {
         this.pattern = pattern;
         return this;
      }

      /**
       * 2-letter ISO country code used in the formatter locale. Defaults to 'US'.
       *
       * @param localeCountry Country ISO code.
       * @return Self.
       */
      public Builder localeCountry(String localeCountry) {
         this.localeCountry = localeCountry;
         return this;
      }

      @Override
      public List<Step> build() {
         if (toVar == null) {
            throw new BenchmarkDefinitionException("Missing toVar attribute");
         }
         if (localeCountry != null && pattern == null) {
            throw new BenchmarkDefinitionException("Country code is used only when the formatter pattern is set.");
         }
         // prevent capturing the builder in the lambda
         String myPattern = pattern;
         String myLocaleCountry = localeCountry;
         return Collections.singletonList(new TimestampStep(SessionFactory.objectAccess(toVar),
               pattern != null ? new Session.ResourceKey<>() {} : null,
               pattern != null ? () -> new FormatterResource(myPattern, myLocaleCountry) : null));
      }
   }
}
