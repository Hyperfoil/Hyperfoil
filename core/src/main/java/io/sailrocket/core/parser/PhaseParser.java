package io.sailrocket.core.parser;

import java.util.Iterator;

import org.yaml.snakeyaml.events.Event;
import org.yaml.snakeyaml.events.MappingEndEvent;
import org.yaml.snakeyaml.events.MappingStartEvent;

import io.sailrocket.core.builders.PhaseBuilder;

abstract class PhaseParser extends AbstractParser<PhaseBuilder.Discriminator, PhaseBuilder> {
   protected PhaseParser() {
      subBuilders.put("startTime", new PropertyParser.String<>(PhaseBuilder::duration));
      subBuilders.put("startAfter", new StartAfterParser(PhaseBuilder::startAfter));
      subBuilders.put("startAfterStrict", new StartAfterParser(PhaseBuilder::startAfterStrict));
      subBuilders.put("duration", new PropertyParser.String<>(PhaseBuilder::duration));
      subBuilders.put("maxDuration", new PropertyParser.String<>(PhaseBuilder::maxDuration));
      subBuilders.put("scenario", new ScenarioParser());
   }

   @Override
   public void parse(Iterator<Event> events, PhaseBuilder.Discriminator target) throws ConfigurationParserException {
      expectEvent(events, MappingStartEvent.class);
      callSubBuilders(events, type(target), MappingEndEvent.class);
   }

   protected abstract PhaseBuilder type(PhaseBuilder.Discriminator discriminator);

   public static class AtOnce extends PhaseParser {
      public AtOnce() {
         subBuilders.put("users", new PropertyParser.Int<>((builder, users) -> ((PhaseBuilder.AtOnce) builder).users(users)));
      }

      @Override
      protected PhaseBuilder type(PhaseBuilder.Discriminator discriminator) {
         return discriminator.atOnce(-1);
      }
   }

   public static class Always extends PhaseParser {
      public Always() {
         subBuilders.put("users", new PropertyParser.Int<>((builder, users) -> ((PhaseBuilder.Always) builder).users(users)));
      }

      @Override
      protected PhaseBuilder type(PhaseBuilder.Discriminator discriminator) {
         return discriminator.always(-1);
      }
   }

   public static class RampPerSec extends PhaseParser {
      public RampPerSec() {
         subBuilders.put("initialUsersPerSec", new PropertyParser.Int<>((builder1, users1) -> ((PhaseBuilder.RampPerSec) builder1).initialUsersPerSec(users1)));
         subBuilders.put("targetUsersPerSec", new PropertyParser.Int<>((builder, users) -> ((PhaseBuilder.RampPerSec) builder).targetUsersPerSec(users)));
      }

      @Override
      protected PhaseBuilder type(PhaseBuilder.Discriminator discriminator) {
         return discriminator.rampPerSec(-1, -1);
      }
   }

   public static class ConstantPerSec extends PhaseParser {
      public ConstantPerSec() {
         subBuilders.put("usersPerSec", new PropertyParser.Int<>((builder, users) -> ((PhaseBuilder.ConstantPerSec) builder).usersPerSec(users)));
      }

      @Override
      protected PhaseBuilder type(PhaseBuilder.Discriminator discriminator) {
         return discriminator.constantPerSec(-1);
      }
   }
}
