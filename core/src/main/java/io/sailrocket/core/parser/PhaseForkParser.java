package io.sailrocket.core.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.yaml.snakeyaml.events.MappingEndEvent;
import org.yaml.snakeyaml.events.ScalarEvent;

import io.sailrocket.core.builders.PhaseBuilder;
import io.sailrocket.core.builders.Rewritable;

/**
 * About forks: while the internals rely on one phase having only one scenario (due to resources allocation etc.)
 * it is convenient to specify several phases at once that differ only in the scenarios and split the users
 * amongst these. Therefore on the parser side we offer a shortcut called forks, which works by creating new phase
 * for each scenario, slicing the users. These forked phases have the same type, duration etc. as the parent phase.
 * The parent phase is replaced by a no-op phase that is scheduled after all the forked phases - therefore other phases
 * can still be scheduled using {@link PhaseBuilder#startAfter(String)} and {@link PhaseBuilder#startAfterStrict(String)}.
 */
class PhaseForkParser implements Parser<io.sailrocket.core.builders.PhaseBuilder> {
   @Override
   public void parse(Context ctx, PhaseBuilder target) throws ConfigurationParserException {
      ctx.pushVar(new ArrayList<ForkBuilder>());
      ctx.parseList(target, this::parseFork);
      List<ForkBuilder> forks = ctx.popVar(List.class);
      double sumWeight = forks.stream().mapToDouble(fb -> fb.weight).sum();
      for (ForkBuilder fb : forks) {
         fb.phaseBuilder.slice(target, fb.weight / sumWeight);
      }
      target.endPhase().proxify(target, forks.stream().map(fb -> fb.phaseBuilder.name()).collect(Collectors.toList()));
   }

   private void parseFork(Context ctx, PhaseBuilder phaseBuilder) throws ConfigurationParserException {
      ScalarEvent forkNameEvent = ctx.expectEvent(ScalarEvent.class);
      ForkBuilder forkBuilder = new ForkBuilder(phaseBuilder.fork(phaseBuilder.name() + "/" + forkNameEvent.getValue()));
      ctx.peekVar(List.class).add(forkBuilder);
      ctx.parseAliased(ForkBuilder.class, forkBuilder, ForkParser.INSTANCE);
      // this belongs to the outer list
      ctx.expectEvent(MappingEndEvent.class);
   }

   static class ForkParser extends AbstractParser<ForkBuilder, ForkBuilder> {
      private static final ForkParser INSTANCE = new ForkParser();

      ForkParser() {
         subBuilders.put("weight", new PropertyParser.Double<>(ForkBuilder::weight));
         subBuilders.put("scenario", new ForkScenarioParser());
      }

      @Override
      public void parse(Context ctx, ForkBuilder target) throws ConfigurationParserException {
         callSubBuilders(ctx, target, MappingEndEvent.class);
      }
   }

   static class ForkBuilder implements Rewritable<ForkBuilder> {
      private PhaseBuilder phaseBuilder;
      private double weight;

      ForkBuilder(PhaseBuilder phaseBuilder) {
         this.phaseBuilder = phaseBuilder;
      }

      public ForkBuilder weight(double weight) {
         this.weight = weight;
         return this;
      }

      @Override
      public void readFrom(ForkBuilder other) {
         other.phaseBuilder.copyScenarioTo(this.phaseBuilder.scenario());
         weight = other.weight;
      }
   }

   static class ForkScenarioParser implements Parser<ForkBuilder> {
      @Override
      public void parse(Context ctx, ForkBuilder target) throws ConfigurationParserException {
         ScenarioParser.instance().parse(ctx, target.phaseBuilder);
      }
   }
}
