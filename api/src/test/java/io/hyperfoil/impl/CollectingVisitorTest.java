package io.hyperfoil.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import io.hyperfoil.api.session.AccessVisitor;
import io.hyperfoil.api.session.ReadAccess;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.session.Session;

public class CollectingVisitorTest {
   // Fail promptly if discovery walks payload elements, without relying on timing or exhausting the test JVM's heap.
   private static final int VISIT_BUDGET = 64;

   private static final Object[] PRIMITIVE_PAYLOADS = {
         new byte[1024 * 1024], new boolean[1024], new char[1024], new short[1024],
         new int[1024], new long[1024], new float[1024], new double[1024]
   };

   @Test
   public void primitivePayloadsDoNotIncreaseResourceDiscoveryWork() {
      ResourceUtilizer resource = session -> {
      };
      for (Object payload : PRIMITIVE_PAYLOADS) {
         ResourceCollector visitor = new ResourceCollector();
         visitor.visit(new Object[] { payload, resource });
         assertEquals(List.of(resource), visitor.resources);
         assertEquals(2, trackedObjects(visitor), "only the root array and the utilizer should be tracked");
      }
   }

   @Test
   public void productionVisitorsSkipPayloadFieldsOfSteps() {
      ResourceUtilizer nested = session -> {
      };
      ReadAccess access = new DummyAccess();
      for (Object payload : PRIMITIVE_PAYLOADS) {
         PayloadStep step = new PayloadStep(payload, nested, access);

         BudgetedResourceVisitor resourceVisitor = new BudgetedResourceVisitor();
         resourceVisitor.visit(step);
         assertEquals(List.of(step, nested), Arrays.asList(resourceVisitor.resourceUtilizers()));

         BudgetedAccessVisitor accessVisitor = new BudgetedAccessVisitor();
         accessVisitor.visit(step);
         assertEquals(List.of(access), Arrays.asList(accessVisitor.reads()));
      }
   }

   @Test
   public void scalarsAreNotTracked() {
      List<String> strings = IntStream.range(0, 10_000).mapToObj(i -> "s" + i).collect(Collectors.toList());
      Integer[] boxed = IntStream.range(0, 10_000).boxed().toArray(Integer[]::new);
      Map<String, Long> map = IntStream.range(0, 10_000).boxed()
            .collect(Collectors.toMap(i -> "k" + i, i -> (long) i));
      ResourceUtilizer resource = session -> {
      };
      Object[] root = { strings, boxed, map, resource };

      ResourceUtilizer.Visitor visitor = new ResourceUtilizer.Visitor();
      visitor.visit(root);

      assertEquals(List.of(resource), Arrays.asList(visitor.resourceUtilizers()));
      assertEquals(5, trackedObjects(visitor), "root, list, array, map and utilizer");
   }

   @Test
   public void referenceArraysStillExposeResourcesAndHandleCycles() {
      ResourceUtilizer first = session -> {
      };
      ResourceUtilizer second = session -> {
      };
      Object[] root = new Object[3];
      root[0] = new Object[] { first, new byte[1024] };
      root[1] = new ResourceUtilizer[] { second, first };
      root[2] = root;

      ResourceCollector visitor = new ResourceCollector();
      visitor.visit(root);

      assertEquals(List.of(first, second), visitor.resources);
   }

   @Test
   public void explicitlyRequestedArrayTypeIsStillCollected() {
      byte[] payload = new byte[1024];
      List<byte[]> collected = new ArrayList<>();
      new CollectingVisitor<byte[]>(byte[].class) {
         @Override
         protected boolean process(byte[] value) {
            collected.add(value);
            return false;
         }
      }.visit(new Object[] { payload, payload });

      assertEquals(1, collected.size());
      assertSame(payload, collected.get(0));
   }

   @Test
   public void explicitlyRequestedScalarTypeIsStillCollected() {
      List<String> collected = new ArrayList<>();
      new CollectingVisitor<String>(String.class) {
         @Override
         protected boolean process(String value) {
            collected.add(value);
            return false;
         }
      }.visit(new Object[] { "foo", List.of("bar"), 42 });

      assertEquals(List.of("foo", "bar"), collected);
   }

   // Package-private members of CollectingVisitor are not inherited by subclasses in other packages.
   private static int trackedObjects(CollectingVisitor<?> visitor) {
      return visitor.trackedObjects();
   }

   private static void checkBudget(int visits) {
      assertTrue(visits <= VISIT_BUDGET, "Resource discovery must not scale with the number of primitive payload elements");
   }

   private static class ResourceCollector extends CollectingVisitor<ResourceUtilizer> {
      final List<ResourceUtilizer> resources = new ArrayList<>();
      int visits;

      ResourceCollector() {
         super(ResourceUtilizer.class);
      }

      @Override
      public boolean visit(String name, Object value, Type fieldType) {
         checkBudget(++visits);
         return super.visit(name, value, fieldType);
      }

      @Override
      protected boolean process(ResourceUtilizer value) {
         resources.add(value);
         return false;
      }
   }

   private static class BudgetedResourceVisitor extends ResourceUtilizer.Visitor {
      int visits;

      @Override
      public boolean visit(String name, Object value, Type fieldType) {
         checkBudget(++visits);
         return super.visit(name, value, fieldType);
      }
   }

   private static class BudgetedAccessVisitor extends AccessVisitor {
      int visits;

      @Override
      public boolean visit(String name, Object value, Type fieldType) {
         checkBudget(++visits);
         return super.visit(name, value, fieldType);
      }
   }

   /**
    * Mimics a step that carries a request body and references further resources and session variables.
    */
   private static class PayloadStep implements ResourceUtilizer {
      final Object body;
      final ResourceUtilizer nested;
      final ReadAccess access;

      PayloadStep(Object body, ResourceUtilizer nested, ReadAccess access) {
         this.body = body;
         this.nested = nested;
         this.access = access;
      }

      @Override
      public void reserve(Session session) {
      }
   }

   private static class DummyAccess implements ReadAccess {
      @Override
      public boolean isSet(Session session) {
         return false;
      }

      @Override
      public Object getObject(Session session) {
         return null;
      }

      @Override
      public int getInt(Session session) {
         return 0;
      }

      @Override
      public boolean isSequenceScoped() {
         return false;
      }

      @Override
      public Session.Var getVar(Session session) {
         return null;
      }

      @Override
      public Object key() {
         return "dummy";
      }

      @Override
      public void setIndex(int index) {
      }

      @Override
      public int index() {
         return 0;
      }
   }
}
