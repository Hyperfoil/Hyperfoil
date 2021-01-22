package io.hyperfoil.cli.commands;

import java.util.function.Function;
import java.util.stream.Stream;

import org.aesh.command.completer.OptionCompleter;

import io.hyperfoil.cli.context.HyperfoilCliContext;
import io.hyperfoil.cli.context.HyperfoilCompleterData;
import io.hyperfoil.client.RestClientException;

public class HyperfoilOptionCompleter implements OptionCompleter<HyperfoilCompleterData> {
   private final Function<HyperfoilCliContext, Stream<String>> provider;

   public HyperfoilOptionCompleter(Function<HyperfoilCliContext, Stream<String>> provider) {
      this.provider = provider;
   }

   @Override
   public void complete(HyperfoilCompleterData completerInvocation) {
      HyperfoilCliContext context = completerInvocation.getContext();
      Stream<String> options;
      try {
         options = provider.apply(context);
      } catch (RestClientException e) {
         return;
      }
      String prefix = completerInvocation.getGivenCompleteValue();
      if (prefix != null) {
         options = options.filter(b -> b.startsWith(prefix));
      }
      options.forEach(completerInvocation::addCompleterValue);
   }
}
