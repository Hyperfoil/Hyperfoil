package io.hyperfoil.grpc.parser;

import org.yaml.snakeyaml.events.SequenceStartEvent;

import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.core.parser.AbstractParser;
import io.hyperfoil.core.parser.Context;
import io.hyperfoil.core.parser.ParserException;
import io.hyperfoil.core.parser.PropertyParser;
import io.hyperfoil.grpc.config.GrpcBuilder;
import io.hyperfoil.grpc.config.GrpcPluginBuilder;

public class GrpcParser extends AbstractParser<BenchmarkBuilder, GrpcBuilder> {

   public GrpcParser() {
      register("name", new PropertyParser.String<>(GrpcBuilder::name));
      register("host", new PropertyParser.String<>(GrpcBuilder::host));
      register("port", new PropertyParser.Int<>(GrpcBuilder::port));
      register("maxStreams", new PropertyParser.Int<>(GrpcBuilder::maxStreams));
      register("sharedConnections", new PropertyParser.Int<>(GrpcBuilder::sharedConnections));
      register("proto", new ProtoConfigParser());
   }

   @Override
   public void parse(Context ctx, BenchmarkBuilder target) throws ParserException {
      GrpcPluginBuilder plugin = target.addPlugin(GrpcPluginBuilder::new);
      if (ctx.peek() instanceof SequenceStartEvent) {
         ctx.parseList(plugin, (ctx1, builder) -> {
            GrpcBuilder grpc = builder.decoupledGrpc();
            callSubBuilders(ctx1, grpc);
            builder.addGrpc(grpc);
         });
      } else {
         callSubBuilders(ctx, plugin.grpc());
      }
   }
}
