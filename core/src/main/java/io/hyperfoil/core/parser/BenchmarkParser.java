/*
 * Copyright 2018 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.hyperfoil.core.parser;

import io.hyperfoil.core.api.Plugin;
import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.BenchmarkBuilder;
import io.hyperfoil.api.config.BenchmarkData;
import io.hyperfoil.core.util.Util;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.events.DocumentEndEvent;
import org.yaml.snakeyaml.events.DocumentStartEvent;
import org.yaml.snakeyaml.events.Event;
import org.yaml.snakeyaml.events.MappingEndEvent;
import org.yaml.snakeyaml.events.MappingStartEvent;
import org.yaml.snakeyaml.events.SequenceEndEvent;
import org.yaml.snakeyaml.events.SequenceStartEvent;
import org.yaml.snakeyaml.events.StreamEndEvent;
import org.yaml.snakeyaml.events.StreamStartEvent;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.Iterator;
import java.util.ServiceLoader;

public class BenchmarkParser extends AbstractMappingParser<BenchmarkBuilder> {
   private static final Logger log = LogManager.getLogger(BenchmarkParser.class);
   private static final BenchmarkParser INSTANCE = new BenchmarkParser();
   private static final boolean DEBUG_PARSER = Boolean.getBoolean("io.hyperfoil.parser.debug");

   public static BenchmarkParser instance() {
      return INSTANCE;
   }

   private BenchmarkParser() {
      register("$schema", new PropertyParser.String<>(this::checkSchema));
      register("name", new PropertyParser.String<>(BenchmarkBuilder::name));
      register("agents", new AgentsParser());
      register("ergonomics", new ErgonomicsParser());
      register("failurePolicy", new PropertyParser.Enum<>(Benchmark.FailurePolicy.values(), BenchmarkBuilder::failurePolicy));
      register("phases", new PhasesParser());
      register("threads", new PropertyParser.Int<>(BenchmarkBuilder::threads));
      register("statisticsCollectionPeriod", new PropertyParser.Int<>(BenchmarkBuilder::statisticsCollectionPeriod));
      // simplified single-phase definition
      register("usersPerSec", new PropertyParser.Double<>((bb, value) -> bb.singleConstantRatePhase().usersPerSec(value)));
      register("duration", new PropertyParser.TimeMillis<>((bb, value) -> bb.singleConstantRatePhase().duration(value)));
      register("maxDuration", new PropertyParser.TimeMillis<>((bb, value) -> bb.singleConstantRatePhase().maxDuration(value)));
      register("maxSessions", new PropertyParser.Int<>((bb, value) -> bb.singleConstantRatePhase().maxSessions(value)));
      register("scenario", (ctx, target) -> new ScenarioParser().parse(ctx, target.singleConstantRatePhase().scenario()));
      register("staircase", new StaircaseParser());
      register("triggerUrl", new PropertyParser.String<>(BenchmarkBuilder::triggerUrl));
      register("pre", new RunHooksParser(BenchmarkBuilder::addPreHook));
      register("post", new RunHooksParser(BenchmarkBuilder::addPostHook));
      ServiceLoader.load(Plugin.class).forEach(instance -> register(instance.name(), instance.parser()));
   }

   private void checkSchema(BenchmarkBuilder builder, String schema) {
      if (schema.startsWith("http") && !schema.startsWith("http://hyperfoil.io/schema") &&
            !schema.startsWith("https://hyperfoil.io/schema")) {
         log.warn("Unexpected schema: should start with `http://hyperfoil.io/schema`!");
      }
   }

   public Benchmark buildBenchmark(String source, BenchmarkData data) throws ParserException {
      return builder(source, data).build();
   }

   public BenchmarkBuilder builder(String source, BenchmarkData data) throws ParserException {
      Yaml yaml = new Yaml();

      Iterator<Event> events = yaml.parse(new StringReader(source)).iterator();
      if (DEBUG_PARSER) {
         events = new DebugIterator<>(events);
      }
      Context ctx = new Context(events);

      ctx.expectEvent(StreamStartEvent.class);
      ctx.expectEvent(DocumentStartEvent.class);

      //instantiate new benchmark builder
      BenchmarkBuilder benchmarkBuilder = new BenchmarkBuilder(source, data);
      parse(ctx, benchmarkBuilder);

      ctx.expectEvent(DocumentEndEvent.class);
      ctx.expectEvent(StreamEndEvent.class);

      return benchmarkBuilder;
   }

   public Benchmark buildBenchmark(InputStream inputStream, BenchmarkData data) throws ParserException, IOException {
      return buildBenchmark(Util.toString(inputStream), data);
   }

   private static class DebugIterator<T> implements Iterator<T> {
      private final Iterator<T> it;
      private String indent = "";

      private DebugIterator(Iterator<T> it) {
         this.it = it;
      }

      @Override
      public boolean hasNext() {
         return it.hasNext();
      }

      @Override
      public T next() {
         T event = it.next();
         if (event instanceof MappingEndEvent || event instanceof SequenceEndEvent) {
            indent = indent.substring(2);
         }
         StackTraceElement[] stackTrace = new Exception().fillInStackTrace().getStackTrace();
         System.out.println(indent + event + " fetched from " + stackTrace[1] + "\t" + stackTrace[2] + "\t" + stackTrace[3]);
         if (event instanceof MappingStartEvent || event instanceof SequenceStartEvent) {
            indent += "| ";
         }
         return event;
      }
   }
}
