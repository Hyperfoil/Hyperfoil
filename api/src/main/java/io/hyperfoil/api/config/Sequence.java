package io.hyperfoil.api.config;

import java.io.Serializable;
import java.util.Objects;
import java.util.stream.Stream;

import io.hyperfoil.api.session.ReadAccess;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.session.WriteAccess;
import io.hyperfoil.impl.ResourceVisitor;

/**
 * Sequences are a series of one or more {@link Step}'s that perform one logical unit of operation. Steps within a Sequence are executed in order.
 * State is shared between sequences via the {@link Session}. This allows sequences to pass request scoped state between {@link Step} invocations.
 * <p>
 * Sequences form the basis of a timed operation.
 */
public class Sequence implements Serializable {

   private final String name;
   private final int id;
   private final int concurrency;
   private final int offset;
   private final Step[] steps;
   private final ReadAccess[] reads;
   private final WriteAccess[] writes;
   private final ResourceUtilizer[] resourceUtilizers;

   public Sequence(String name, int id, int concurrency, int offset, Step[] steps) {
      this.name = name;
      this.id = id;
      this.concurrency = concurrency;
      this.offset = offset;
      this.steps = steps;
      ResourceVisitor visitor = new ResourceVisitor(this);
      this.resourceUtilizers = visitor.resourceUtilizers();
      this.reads = visitor.reads();
      this.writes = visitor.writes();
   }

   public int id() {
      return id;
   }

   public int concurrency() {
      return concurrency;
   }

   /**
    * @return Index for first instance for cases where we need an array of all concurrent instances.
    */
   public int offset() {
      return offset;
   }

   public void reserve(Session session) {
      for (WriteAccess access : writes) {
         access.reserve(session);
      }
      for (ResourceUtilizer ru : resourceUtilizers) {
         ru.reserve(session);
      }
   }

   public String name() {
      return name;
   }

   public Step[] steps() {
      return steps;
   }

   Stream<Object> readKeys() {
      return Stream.of(reads).map(ReadAccess::key).filter(Objects::nonNull);
   }

   Stream<Object> writtenKeys() {
      return Stream.of(writes).map(ReadAccess::key).filter(Objects::nonNull);
   }
}
