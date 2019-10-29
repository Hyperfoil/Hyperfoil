package io.hyperfoil.core.parser;

import io.hyperfoil.api.config.PhaseBuilder;

abstract class PhaseParser extends AbstractParser<PhaseBuilder.Catalog, PhaseBuilder> {
   PhaseParser() {
      register("startTime", new PropertyParser.String<>(PhaseBuilder::startTime));
      register("startAfter", new StartAfterParser(PhaseBuilder::startAfter));
      register("startAfterStrict", new StartAfterParser(PhaseBuilder::startAfterStrict));
      register("duration", new PropertyParser.String<>(PhaseBuilder::duration));
      register("maxDuration", new PropertyParser.String<>(PhaseBuilder::maxDuration));
      register("maxIterations", new PropertyParser.Int<>(PhaseBuilder::maxIterations));
      register("scenario", new Adapter<>(PhaseBuilder::scenario, new ScenarioParser()));
      register("forks", new PhaseForkParser());
   }

   @Override
   public void parse(Context ctx, PhaseBuilder.Catalog target) throws ParserException {
      callSubBuilders(ctx, type(target));
   }

   protected abstract PhaseBuilder type(PhaseBuilder.Catalog catalog);

   static class AtOnce extends PhaseParser {
      AtOnce() {
         register("users", new IncrementPropertyParser.Int<>((builder, base, inc) -> ((PhaseBuilder.AtOnce) builder).users(base, inc)));
      }

      @Override
      protected PhaseBuilder type(PhaseBuilder.Catalog catalog) {
         return catalog.atOnce(-1);
      }
   }

   static class Always extends PhaseParser {
      Always() {
         register("users", new IncrementPropertyParser.Int<>((builder, base, inc) -> ((PhaseBuilder.Always) builder).users(base, inc)));
      }

      @Override
      protected PhaseBuilder type(PhaseBuilder.Catalog catalog) {
         return catalog.always(-1);
      }
   }

   static class RampPerSec extends PhaseParser {
      RampPerSec() {
         register("initialUsersPerSec", new IncrementPropertyParser.Double<>((builder, base, inc) -> ((PhaseBuilder.RampPerSec) builder).initialUsersPerSec(base, inc)));
         register("targetUsersPerSec", new IncrementPropertyParser.Double<>((builder, base, inc) -> ((PhaseBuilder.RampPerSec) builder).targetUsersPerSec(base, inc)));
         register("maxSessions", new PropertyParser.Int<>((builder, sessions) -> ((PhaseBuilder.RampPerSec) builder).maxSessions(sessions)));
         register("variance", new PropertyParser.Boolean<>((builder, variance) -> ((PhaseBuilder.RampPerSec) builder).variance(variance)));
      }

      @Override
      protected PhaseBuilder type(PhaseBuilder.Catalog catalog) {
         return catalog.rampPerSec(-1, -1);
      }
   }

   static class ConstantPerSec extends PhaseParser {
      ConstantPerSec() {
         register("usersPerSec", new IncrementPropertyParser.Double<>((builder, base, inc) -> ((PhaseBuilder.ConstantPerSec) builder).usersPerSec(base, inc)));
         register("maxSessions", new PropertyParser.Int<>((builder, sessions) -> ((PhaseBuilder.ConstantPerSec) builder).maxSessions(sessions)));
         register("variance", new PropertyParser.Boolean<>((builder, variance) -> ((PhaseBuilder.ConstantPerSec) builder).variance(variance)));
      }

      @Override
      protected PhaseBuilder type(PhaseBuilder.Catalog catalog) {
         return catalog.constantPerSec(-1);
      }
   }
}
