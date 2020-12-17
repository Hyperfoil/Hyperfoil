package io.hyperfoil.core.handlers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.Embed;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.processor.Processor;
import io.hyperfoil.api.processor.RequestProcessorBuilder;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.builders.Condition;
import io.hyperfoil.core.builders.ServiceLoadedBuilderProvider;
import io.netty.buffer.ByteBuf;

public class ConditionalProcessor implements Processor, ResourceUtilizer {
   private final Condition condition;
   private final Processor[] processors;

   public ConditionalProcessor(Condition condition, Processor[] processors) {
      this.condition = condition;
      this.processors = processors;
   }

   @Override
   public void before(Session session) {
      if (condition.test(session)) {
         for (Processor p : processors) {
            p.before(session);
         }
      }
   }

   @Override
   public void process(Session session, ByteBuf data, int offset, int length, boolean isLastPart) {
      if (condition.test(session)) {
         for (Processor p : processors) {
            p.process(session, data, offset, length, isLastPart);
         }
      }
   }

   @Override
   public void after(Session session) {
      if (condition.test(session)) {
         for (Processor p : processors) {
            p.after(session);
         }
      }
   }

   @Override
   public void reserve(Session session) {
      ResourceUtilizer.reserve(session, (Object[]) processors);
   }

   /**
    * Passes the data to nested processor if the condition holds.
    * Note that the condition may be evaluated multiple times and therefore
    * any nested processors should not change the results of the condition.
    */
   @MetaInfServices(RequestProcessorBuilder.class)
   @Name("conditional")
   public static class Builder implements RequestProcessorBuilder {
      private List<Processor.Builder<?>> processors = new ArrayList<>();
      private Condition.TypesBuilder<Builder> condition = new Condition.TypesBuilder<>(this);

      @Embed
      public Condition.TypesBuilder<Builder> condition() {
         return condition;
      }

      public Builder processor(Processor.Builder<?> processor) {
         this.processors.add(processor);
         return this;
      }

      public Builder processors(Collection<? extends Processor.Builder<?>> processors) {
         this.processors.addAll(processors);
         return this;
      }

      /**
       * One or more processors that should be invoked if the condition holds.
       *
       * @return Builder.
       */
      public ServiceLoadedBuilderProvider<RequestProcessorBuilder> processor() {
         return new ServiceLoadedBuilderProvider<>(RequestProcessorBuilder.class, this::processor);
      }

      @Override
      public Processor build(boolean fragmented) {
         if (processors.isEmpty()) {
            throw new BenchmarkDefinitionException("Conditional processor does not delegate to any processors.");
         }
         Condition condition = this.condition.buildCondition();
         if (condition == null) {
            throw new BenchmarkDefinitionException("Conditional processor must specify a condition.");
         }
         return new ConditionalProcessor(condition, processors.stream().map(pb -> pb.build(fragmented)).toArray(Processor[]::new));
      }
   }
}
