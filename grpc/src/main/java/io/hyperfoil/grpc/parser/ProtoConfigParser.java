package io.hyperfoil.grpc.parser;

import org.yaml.snakeyaml.events.Event;
import org.yaml.snakeyaml.events.MappingStartEvent;
import org.yaml.snakeyaml.events.ScalarEvent;

import io.hyperfoil.core.parser.AbstractParser;
import io.hyperfoil.core.parser.Context;
import io.hyperfoil.core.parser.Parser;
import io.hyperfoil.core.parser.ParserException;
import io.hyperfoil.core.parser.PropertyParser;
import io.hyperfoil.grpc.config.GrpcBuilder;
import io.hyperfoil.grpc.config.ProtoConfig;

public class ProtoConfigParser extends AbstractParser<GrpcBuilder, ProtoConfig.Builder> {

   public ProtoConfigParser() {
      register("main", new PropertyParser.String<>(ProtoConfig.Builder::main));
      register("importProtos", ProtoConfigParser::parseImportProtos);
   }

   private static void parseImportProtos(Context ctx, ProtoConfig.Builder builder) throws ParserException {
      if (ctx.peek() instanceof ScalarEvent) {
         String value = ctx.expectEvent(ScalarEvent.class).getValue();
         if (value != null && !value.isEmpty()) {
            builder.addImportProto(value);
         }
      } else {
         ctx.parseList(builder, ImportProtosParser.Instance);
      }
   }

   @Override
   public void parse(Context ctx, GrpcBuilder grpc) throws ParserException {
      Event event = ctx.peek();
      if (event instanceof MappingStartEvent) {
         callSubBuilders(ctx, grpc.proto());
      } else {
         throw ctx.unexpectedEvent(event);
      }
   }

   private enum ImportProtosParser implements Parser<ProtoConfig.Builder> {
      Instance;

      @Override
      public void parse(Context ctx, ProtoConfig.Builder target) throws ParserException {
         ScalarEvent event = ctx.expectEvent(ScalarEvent.class);
         target.addImportProto(event.getValue());
      }
   }
}
