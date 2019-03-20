package io.hyperfoil.core.parser;

import org.yaml.snakeyaml.events.MappingEndEvent;
import org.yaml.snakeyaml.events.MappingStartEvent;
import org.yaml.snakeyaml.events.ScalarEvent;

import io.hyperfoil.api.config.PhaseBuilder;
import io.hyperfoil.api.config.PhaseForkBuilder;

/**
 * About forks: while the internals rely on one phase having only one scenario (due to resources allocation etc.)
 * it is convenient to specify several phases at once that differ only in the scenarios and split the users
 * amongst these. Therefore on the builder side we offer a shortcut called forks, which works by creating new phase
 * for each scenario, slicing the users. These forked phases have the same type, duration etc. as the parent phase.
 * The parent phase is replaced by a no-op phase that is scheduled after all the forked phases - therefore other phases
 * can still be scheduled using {@link PhaseBuilder#startAfter(String)} and {@link PhaseBuilder#startAfterStrict(String)}.
 */
class PhaseForkParser implements Parser<PhaseBuilder> {
   @Override
   public void parse(Context ctx, PhaseBuilder target) throws ParserException {
      ctx.parseList(target, this::parseFork);
   }

   private void parseFork(Context ctx, PhaseBuilder phaseBuilder) throws ParserException {
      ctx.expectEvent(MappingStartEvent.class);
      ScalarEvent forkNameEvent = ctx.expectEvent(ScalarEvent.class);
      PhaseForkBuilder forkBuilder = phaseBuilder.fork(forkNameEvent.getValue());
      ctx.parseAliased(PhaseForkBuilder.class, forkBuilder, ForkParser.INSTANCE);
      // this belongs to the outer list
      ctx.expectEvent(MappingEndEvent.class);
   }

   static class ForkParser extends AbstractMappingParser<PhaseForkBuilder> {
      private static final ForkParser INSTANCE = new ForkParser();

      ForkParser() {
         register("weight", new PropertyParser.Double<>(PhaseForkBuilder::weight));
         register("scenario", new Adapter<>(PhaseForkBuilder::scenario, new ScenarioParser()));
      }
   }
}
