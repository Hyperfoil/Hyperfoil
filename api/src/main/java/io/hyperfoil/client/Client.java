package io.hyperfoil.client;

import java.util.Collection;
import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.hyperfoil.api.config.Benchmark;

/**
 * API for server control
 */
public interface Client {
   Collection<Agent> agents();

   BenchmarkRef register(Benchmark benchmark);

   List<String> benchmarks();

   BenchmarkRef benchmark(String name);

   List<String> runs();

   RunRef run(String id);

   interface BenchmarkRef {
      String name();
      Benchmark get();
      RunRef start();
   }

   interface RunRef {
      String id();
      Run get();
      RunRef kill();
      // TODO: server should expose JSON-formatted variants
      Collection<String> sessions();
      Collection<String> connections();
      String statsRecent();
      String statsTotal();
   }

   class Agent {
      public final String name;
      public final String address;
      public final String status;

      @JsonCreator
      public Agent(@JsonProperty("name") String name, @JsonProperty("address") String address, @JsonProperty("status") String status) {
         this.name = name;
         this.address = address;
         this.status = status;
      }
   }

   class Phase {
      public final String name;
      public final String status;
      @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy/MM/dd HH:mm:ss.S")
      public final Date started;
      public final String remaining;
      @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy/MM/dd HH:mm:ss.S")
      public final Date finished;
      public final String totalDuration;

      @JsonCreator
      public Phase(@JsonProperty("name") String name, @JsonProperty("status") String status,
                   @JsonProperty("started") Date started, @JsonProperty("remaining") String remaining,
                   @JsonProperty("finished") Date finished, @JsonProperty("totalDuration") String totalDuration) {
         this.name = name;
         this.status = status;
         this.started = started;
         this.remaining = remaining;
         this.finished = finished;
         this.totalDuration = totalDuration;
      }
   }

   class Run {
      public final String id;
      public final String benchmark;
      @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy/MM/dd HH:mm:ss.S")
      public final Date started;
      @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy/MM/dd HH:mm:ss.S")
      public final Date terminated;
      public final String description;
      public final Collection<Phase> phases;
      public final Collection<Agent> agents;

      @JsonCreator
      public Run(@JsonProperty("id") String id, @JsonProperty("benchmark") String benchmark,
                 @JsonProperty("started") Date started, @JsonProperty("terminated") Date terminated,
                 @JsonProperty("description") String description,
                 @JsonProperty("phases") Collection<Phase> phases,
                 @JsonProperty("agents") Collection<Agent> agents) {
         this.id = id;
         this.benchmark = benchmark;
         this.started = started;
         this.terminated = terminated;
         this.description = description;
         this.phases = phases;
         this.agents = agents;
      }

   }
}
