package io.sailrocket.core.parser;

import io.sailrocket.core.builders.PhaseBuilder;

abstract class PhaseParser extends AbstractParser<PhaseBuilder.Discriminator, PhaseBuilder> {
   protected PhaseParser() {
      register("startTime", new PropertyParser.String<>(PhaseBuilder::duration));
      register("startAfter", new StartAfterParser(PhaseBuilder::startAfter));
      register("startAfterStrict", new StartAfterParser(PhaseBuilder::startAfterStrict));
      register("duration", new PropertyParser.String<>(PhaseBuilder::duration));
      register("maxDuration", new PropertyParser.String<>(PhaseBuilder::maxDuration));
      register("maxIterations", new PropertyParser.Int<>(PhaseBuilder::maxIterations));
      register("scenario", new Adapter<>(PhaseBuilder::scenario, new ScenarioParser()));
      register("forks", new PhaseForkParser());
   }

   @Override
   public void parse(Context ctx, PhaseBuilder.Discriminator target) throws ConfigurationParserException {
      callSubBuilders(ctx, type(target));
   }

   protected abstract PhaseBuilder type(PhaseBuilder.Discriminator discriminator);

   public static class AtOnce extends PhaseParser {
      public AtOnce() {
         register("users", new IncrementPropertyParser.Int<>((builder, base, inc) -> ((PhaseBuilder.AtOnce) builder).users(base, inc)));
      }

      @Override
      protected PhaseBuilder type(PhaseBuilder.Discriminator discriminator) {
         return discriminator.atOnce(-1);
      }
   }

   public static class Always extends PhaseParser {
      public Always() {
         register("users", new IncrementPropertyParser.Int<>((builder, base, inc) -> ((PhaseBuilder.Always) builder).users(base, inc)));
      }

      @Override
      protected PhaseBuilder type(PhaseBuilder.Discriminator discriminator) {
         return discriminator.always(-1);
      }
   }

   public static class RampPerSec extends PhaseParser {
      public RampPerSec() {
         register("initialUsersPerSec", new IncrementPropertyParser.Double<>((builder, base, inc) -> ((PhaseBuilder.RampPerSec) builder).initialUsersPerSec(base, inc)));
         register("targetUsersPerSec", new IncrementPropertyParser.Double<>((builder, base, inc) -> ((PhaseBuilder.RampPerSec) builder).targetUsersPerSec(base, inc)));
         register("maxSessionsEstimate", new PropertyParser.Int<>((builder, sessions) -> ((PhaseBuilder.RampPerSec) builder).maxSessionsEstimate(sessions)));
      }

      @Override
      protected PhaseBuilder type(PhaseBuilder.Discriminator discriminator) {
         return discriminator.rampPerSec(-1, -1);
      }
   }

   public static class ConstantPerSec extends PhaseParser {
      public ConstantPerSec() {
         register("usersPerSec", new IncrementPropertyParser.Double<>((builder, base, inc) -> ((PhaseBuilder.ConstantPerSec) builder).usersPerSec(base, inc)));
         register("maxSessionsEstimate", new PropertyParser.Int<>((builder, sessions) -> ((PhaseBuilder.ConstantPerSec) builder).maxSessionsEstimate(sessions)));
      }

      @Override
      protected PhaseBuilder type(PhaseBuilder.Discriminator discriminator) {
         return discriminator.constantPerSec(-1);
      }
   }
}
