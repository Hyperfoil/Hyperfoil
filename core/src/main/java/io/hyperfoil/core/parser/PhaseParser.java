package io.hyperfoil.core.parser;

import java.util.function.Predicate;

import io.hyperfoil.api.config.Model;
import io.hyperfoil.api.config.PhaseBuilder;
import io.hyperfoil.api.config.SessionLimitPolicy;

abstract class PhaseParser extends AbstractParser<PhaseBuilder.Catalog, PhaseBuilder<?>> {

   PhaseParser() {
      register("startTime", new PropertyParser.TimeMillis<>(PhaseBuilder::startTime));
      register("startAfter", new StartAfterParser(PhaseBuilder::startAfter));
      register("startAfterStrict", new StartAfterParser(PhaseBuilder::startAfterStrict));
      register("duration", new PropertyParser.TimeMillis<>(PhaseBuilder::duration));
      register("maxDuration", new PropertyParser.TimeMillis<>(PhaseBuilder::maxDuration));
      register("maxIterations", new PropertyParser.Int<>(PhaseBuilder::maxIterations));
   }

   @Override
   public void parse(Context ctx, PhaseBuilder.Catalog target) throws ParserException {
      callSubBuilders(ctx, type(target));
   }

   protected abstract PhaseBuilder<?> type(PhaseBuilder.Catalog catalog);

   static class Noop extends PhaseParser {
      @Override
      protected PhaseBuilder<?> type(PhaseBuilder.Catalog catalog) {
         return catalog.noop();
      }
   }

   abstract static class BasePhaseParser extends PhaseParser {
      BasePhaseParser() {
         register("scenario", new Adapter<>(PhaseBuilder::scenario, new ScenarioParser()));
         register("forks", new PhaseForkParser());
      }
   }

   static class AtOnce extends BasePhaseParser {
      AtOnce() {
         register("users", new IncrementPropertyParser.Int<>((builder, base, inc) -> ((PhaseBuilder.AtOnce) builder).users(base, inc)));
      }

      @Override
      protected PhaseBuilder.AtOnce type(PhaseBuilder.Catalog catalog) {
         return catalog.atOnce(-1);
      }
   }

   static class Always extends BasePhaseParser {
      Always() {
         register("users", new IncrementPropertyParser.Int<>((builder, base, inc) -> ((PhaseBuilder.Always) builder).users(base, inc)));
      }

      @Override
      protected PhaseBuilder.Always type(PhaseBuilder.Catalog catalog) {
         return catalog.always(-1);
      }
   }

   abstract static class OpenModel extends BasePhaseParser {
      OpenModel() {
         register("maxSessions", new PropertyParser.Int<>((builder, sessions) -> ((PhaseBuilder.OpenModel<?>) builder).maxSessions(sessions)));
         register("variance", new PropertyParser.Boolean<>((builder, variance) -> ((PhaseBuilder.OpenModel<?>) builder).variance(variance)));
         register("sessionLimitPolicy", new PropertyParser.Enum<>(SessionLimitPolicy.values(), (builder, policy) -> ((PhaseBuilder.OpenModel<?>) builder).sessionLimitPolicy(policy)));
      }
   }

   static class RampRate extends OpenModel {
      Predicate<Model.RampRate> constraint;
      String constraintMessage;

      RampRate() {
         register("initialUsersPerSec", new IncrementPropertyParser.Double<>((builder, base, inc) -> ((PhaseBuilder.RampRate) builder).initialUsersPerSec(base, inc)));
         register("targetUsersPerSec", new IncrementPropertyParser.Double<>((builder, base, inc) -> ((PhaseBuilder.RampRate) builder).targetUsersPerSec(base, inc)));
      }

      @Override
      protected PhaseBuilder.RampRate type(PhaseBuilder.Catalog catalog) {
         return catalog.rampRate(-1, -1).constraint(constraint, constraintMessage);
      }

      RampRate constraint(Predicate<Model.RampRate> constraint, String message) {
         this.constraint = constraint;
         this.constraintMessage = message;
         return this;
      }
   }

   static class ConstantRate extends OpenModel {
      ConstantRate() {
         register("usersPerSec", new IncrementPropertyParser.Double<>((builder, base, inc) -> ((PhaseBuilder.ConstantRate) builder).usersPerSec(base, inc)));
      }

      @Override
      protected PhaseBuilder.ConstantRate type(PhaseBuilder.Catalog catalog) {
         return catalog.constantRate(-1);
      }
   }
}
