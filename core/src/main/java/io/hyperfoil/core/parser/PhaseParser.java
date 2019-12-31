package io.hyperfoil.core.parser;

import io.hyperfoil.api.config.Phase;
import io.hyperfoil.api.config.PhaseBuilder;

abstract class PhaseParser extends AbstractParser<PhaseBuilder.Catalog, PhaseBuilder<?>> {
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

   protected abstract PhaseBuilder<?> type(PhaseBuilder.Catalog catalog);

   static class AtOnce extends PhaseParser {
      AtOnce() {
         register("users", new IncrementPropertyParser.Int<>((builder, base, inc) -> ((PhaseBuilder.AtOnce) builder).users(base, inc)));
      }

      @Override
      protected PhaseBuilder.AtOnce type(PhaseBuilder.Catalog catalog) {
         return catalog.atOnce(-1);
      }
   }

   static class Always extends PhaseParser {
      Always() {
         register("users", new IncrementPropertyParser.Int<>((builder, base, inc) -> ((PhaseBuilder.Always) builder).users(base, inc)));
      }

      @Override
      protected PhaseBuilder.Always type(PhaseBuilder.Catalog catalog) {
         return catalog.always(-1);
      }
   }

   abstract static class OpenModel extends PhaseParser {
      OpenModel() {
         register("maxSessions", new PropertyParser.Int<>((builder, sessions) -> ((PhaseBuilder.OpenModel<?>) builder).maxSessions(sessions)));
         register("variance", new PropertyParser.Boolean<>((builder, variance) -> ((PhaseBuilder.OpenModel<?>) builder).variance(variance)));
         register("sessionLimitPolicy", new PropertyParser.Enum<>(Phase.SessionLimitPolicy.values(), (builder, policy) -> ((PhaseBuilder.OpenModel<?>) builder).sessionLimitPolicy(policy)));
      }
   }

   static class RampPerSec extends OpenModel {
      RampPerSec() {
         register("initialUsersPerSec", new IncrementPropertyParser.Double<>((builder, base, inc) -> ((PhaseBuilder.RampPerSec) builder).initialUsersPerSec(base, inc)));
         register("targetUsersPerSec", new IncrementPropertyParser.Double<>((builder, base, inc) -> ((PhaseBuilder.RampPerSec) builder).targetUsersPerSec(base, inc)));
      }

      @Override
      protected PhaseBuilder.RampPerSec type(PhaseBuilder.Catalog catalog) {
         return catalog.rampPerSec(-1, -1);
      }
   }

   static class ConstantPerSec extends OpenModel {
      ConstantPerSec() {
         register("usersPerSec", new IncrementPropertyParser.Double<>((builder, base, inc) -> ((PhaseBuilder.ConstantPerSec) builder).usersPerSec(base, inc)));
      }

      @Override
      protected PhaseBuilder.ConstantPerSec type(PhaseBuilder.Catalog catalog) {
         return catalog.constantPerSec(-1);
      }
   }
}
