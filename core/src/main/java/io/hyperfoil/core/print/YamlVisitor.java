package io.hyperfoil.core.print;

import java.io.PrintStream;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.Stack;
import java.util.stream.Stream;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.config.Phase;
import io.hyperfoil.api.config.Scenario;
import io.hyperfoil.api.config.Sequence;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.config.Visitor;
import io.hyperfoil.api.processor.Processor;
import io.hyperfoil.api.processor.Transformer;
import io.hyperfoil.api.session.Action;
import io.hyperfoil.impl.ReflectionAcceptor;

public class YamlVisitor implements Visitor {
   private static final Class<?> BYTE_ARRAY = byte[].class;
   private static final Class<?> CHAR_ARRAY = char[].class;
   private int indent = 0;
   private boolean skipIndent = false;
   private boolean addLine = false;
   private final PrintStream stream;
   private Stack<Object> path = new Stack<>();

   public YamlVisitor(PrintStream stream) {
      this.stream = stream;
   }

   public void walk(Benchmark benchmark) {
      ReflectionAcceptor.accept(benchmark, this);
   }

   @Override
   public boolean visit(String name, Object value, Type fieldType) {
      if (value == null) {
         return false;
      }
      if (addLine) {
         stream.println();
         addLine = false;
      }
      if (skipIndent) {
         skipIndent = false;
      } else {
         printIndent();
      }
      if (name.contains(":")) {
         stream.print('"');
         stream.print(name.replaceAll("\"", "\\\""));
         stream.print('"');
      } else {
         stream.print(name);
      }
      stream.print(": ");
      if (path.contains(value)) {
         stream.println("<recursion detected>");
         return true;
      } else {
         path.push(value);
      }
      try {
         printValue(value, false, fieldType);
      } finally {
         path.pop();
      }
      return true;
   }

   protected void printValue(Object value, boolean isList, Type fieldType) {
      if (ReflectionAcceptor.isScalar(value)) {
         printMultiline(String.valueOf(value));
         return;
      }
      Class<?> cls = value.getClass();
      if (cls.isArray()) {
         if (BYTE_ARRAY.isInstance(value)) {
            printMultiline(new String((byte[]) value, StandardCharsets.UTF_8));
         } else if (CHAR_ARRAY.isInstance(value)) {
            printMultiline(String.valueOf((char[]) value));
         } else {
            printArray(value, cls);
         }
      } else if (Collection.class.isAssignableFrom(cls)) {
         printCollection(fieldType, (Collection<?>) value);
      } else if (Map.class.isAssignableFrom(cls)) {
         printMap(fieldType, (Map<?, ?>) value);
      } else if (Sequence.class.isAssignableFrom(cls)) {
         printSequence((Sequence) value);
      } else if (Phase.class.isAssignableFrom(cls)) {
         printPhase((Phase) value, cls);
      } else if (Scenario.class.isAssignableFrom(cls)) {
         printScenario((Scenario) value);
      } else {
         boolean onlyImpl = isOnlyImpl(fieldType, cls);
         if (!onlyImpl) {
            indent += 2;
            if (!isList) {
               stream.println();
               printIndent();
            }
            stream.print(getName(value, cls));
            stream.print(": ");
            addLine = true;
            skipIndent = false;
         }
         if (!isList) {
            indent += 2;
            addLine = true;
            skipIndent = false;
         }
         if (ReflectionAcceptor.accept(value, this) == 0) {
            stream.println("{}");
            addLine = false;
         }
         if (!onlyImpl) {
            indent -= 2;
         }
         if (!isList) {
            indent -= 2;
         }
      }
   }

   private void printMultiline(String s) {
      if (!s.contains("\n")) {
         stream.println(s);
         return;
      }
      stream.println("|+");
      indent += 2;
      for (String line : s.split("\n")) {
         printIndent();
         stream.println(line);
      }
      indent -= 2;
   }

   private void printArray(Object value, Class<?> cls) {
      int length = Array.getLength(value);
      if (length == 0) {
         stream.println("[]");
      } else {
         stream.println();
         for (int i = 0; i < length; ++i) {
            printItem(Array.get(value, i), cls.getComponentType());
         }
      }
   }

   private void printCollection(Type fieldType, Collection<?> collection) {
      Type itemType = Object.class;
      if (fieldType instanceof ParameterizedType && Collection.class.isAssignableFrom((Class<?>) ((ParameterizedType) fieldType).getRawType())) {
         Type[] types = ((ParameterizedType) fieldType).getActualTypeArguments();
         // this won't work with some strange hierarchy of maps
         if (types.length >= 1) {
            itemType = types[0];
         }
      }
      if (collection.isEmpty()) {
         stream.println("[]");
      } else {
         stream.println();
         for (Object item : collection) {
            printItem(item, itemType);
         }
      }
   }

   private void printMap(Type fieldType, Map<?, ?> map) {
      Type valueType = Object.class;
      if (fieldType instanceof ParameterizedType && Map.class.isAssignableFrom((Class<?>) ((ParameterizedType) fieldType).getRawType())) {
         Type[] types = ((ParameterizedType) fieldType).getActualTypeArguments();
         // this won't work with some strange hierarchy of maps
         if (types.length >= 2) {
            valueType = types[1];
         }
      }
      if (map.isEmpty()) {
         stream.println("{}");
      } else {
         stream.println();
         indent += 2;
         for (Map.Entry<?, ?> entry : map.entrySet()) {
            visit(String.valueOf(entry.getKey()), entry.getValue(), valueType);
         }
         indent -= 2;
      }
   }

   private void printSequence(Sequence seq) {
      stream.print(seq.name());
      if (seq.concurrency() > 0) {
         stream.printf("[%d]", seq.concurrency());
      }
      stream.println(": ");
      for (Step step : seq.steps()) {
         printItem(step, Step.class);
      }
   }

   private void printPhase(Phase phase, Class<?> cls) {
      stream.print(phase.name);
      stream.println(": ");
      indent += 2;
      printIndent();
      stream.print(getName(phase, cls));
      stream.println(": ");
      skipIndent = false;
      indent += 2;
      ReflectionAcceptor.accept(phase, this);
      indent -= 4;
   }

   private void printScenario(Scenario scenario) {
      stream.println();
      indent += 2;
      printIndent();
      stream.println("initialSequences:");
      for (Sequence s : scenario.initialSequences()) {
         printItem(s, Sequence.class);
      }
      printIndent();
      stream.println("sequences:");
      Stream.of(scenario.sequences())
            .filter(s -> Stream.of(scenario.initialSequences()).noneMatch(s2 -> s == s2))
            .forEach(s -> printItem(s, Sequence.class));
      indent -= 2;
   }

   private String getName(Object value, Class<?> cls) {
      String suffix = Stream.of(Step.class, Action.class, Processor.class, Transformer.class)
            .filter(c -> c.isAssignableFrom(cls)).map(Class::getSimpleName).findFirst().orElse(null);
      String name = value.getClass().getSimpleName();
      if (value.getClass().isSynthetic()) {
         name = lambdaName(value, value.getClass());
      } else if (suffix != null && name.endsWith(suffix)) {
         name = Character.toLowerCase(name.charAt(0)) + name.substring(1, name.length() - suffix.length());
      } else {
         name = Character.toLowerCase(name.charAt(0)) + name.substring(1);
      }
      return name;
   }

   private boolean isOnlyImpl(Type fieldType, Class<?> cls) {
      if (fieldType == cls) {
         return true;
      } else if (fieldType instanceof ParameterizedType) {
         return ((ParameterizedType) fieldType).getRawType() == cls;
      } else {
         return false;
      }
   }

   private String lambdaName(Object value, Class<?> cls) {
      try {
         Method writeReplace = cls.getDeclaredMethod("writeReplace");
         writeReplace.setAccessible(true);
         SerializedLambda serializedLambda = (SerializedLambda) writeReplace.invoke(value);
         String implClass = serializedLambda.getImplClass();
         String methodName = serializedLambda.getImplMethodName();
         if (methodName.startsWith("lambda$")) {
            methodName = "<lambda>";
         }
         return implClass.substring(implClass.lastIndexOf('/') + 1) + "::" + methodName;
      } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
         return "<lambda>";
      }
   }

   private void printIndent() {
      for (int i = 0; i < indent; ++i) {
         stream.print(' ');
      }
   }

   private void printItem(Object item, Type fieldType) {
      if (item == null) {
         return;
      }
      printIndent();
      stream.print("- ");
      skipIndent = true;
      indent += 2;
      printValue(item, true, fieldType);
      indent -= 2;
      skipIndent = false;
   }
}
