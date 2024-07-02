package io.hyperfoil.schema;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Stack;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.Problem;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.nodeTypes.NodeWithJavadoc;
import com.github.javaparser.ast.nodeTypes.NodeWithSimpleName;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.javadoc.Javadoc;
import com.github.javaparser.javadoc.JavadocBlockTag;

import io.hyperfoil.api.config.BaseSequenceBuilder;
import io.hyperfoil.api.config.InitFromParam;
import io.hyperfoil.api.config.ListBuilder;
import io.hyperfoil.api.config.MappingListBuilder;
import io.hyperfoil.api.config.PairBuilder;
import io.hyperfoil.api.config.PartialBuilder;
import io.hyperfoil.api.config.StepBuilder;
import io.hyperfoil.api.processor.Processor;
import io.hyperfoil.api.session.Action;
import io.hyperfoil.core.builders.BuilderInfo;
import io.hyperfoil.core.builders.ServiceLoadedBuilderProvider;

public class DocsGenerator extends BaseGenerator {
   private static final Set<Class<?>> BLACKLIST = new HashSet<>(Arrays.asList(
         BaseSequenceBuilder.class, ListBuilder.class, MappingListBuilder.class,
         PairBuilder.class, PairBuilder.OfString.class, PairBuilder.OfDouble.class,
         PartialBuilder.class));

   private static final String NO_DESCRIPTION = "<font color=\"#606060\">&lt;no description&gt;</font>";
   private static final Docs EMPTY_DOCS = new Docs(null);
   private final List<Path> sourceDirs;
   private final Path output;
   private final Map<String, Docs> steps = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
   private final Map<Class<?>, Docs> docs = new HashMap<>();
   private final Map<Docs, Class<?>> reverseTypes = new HashMap<>();
   private final JavaParser parser = new JavaParser();
   private final List<Docs> needsResolve = new ArrayList<>();

   public static void main(String[] args) {
      List<Path> sourceDirs = new ArrayList<>();
      for (int i = 0; i < args.length - 1; ++i) {
         sourceDirs.add(Paths.get(args[i]));
      }
      if (args.length > 0) {
         new DocsGenerator(sourceDirs, Paths.get(args[args.length - 1])).run();
      }
   }

   private DocsGenerator(List<Path> sourceDirs, Path output) {
      this.sourceDirs = sourceDirs;
      this.output = output;
   }

   private void run() {
      for (Map.Entry<String, BuilderInfo<?>> entry : ServiceLoadedBuilderProvider.builders(StepBuilder.class).entrySet()) {
         @SuppressWarnings("unchecked")
         Class<? extends StepBuilder<?>> newBuilder = (Class<? extends StepBuilder<?>>) entry.getValue().implClazz;
         if (newBuilder.isAnnotationPresent(Deprecated.class)) {
            continue;
         }
         ClassOrInterfaceDeclaration cd = findClass(newBuilder);
         if (cd != null) {
            String inlineParamDocs = findInlineParamDocs(cd);
            addStep(entry.getKey(), newBuilder, InitFromParam.class.isAssignableFrom(newBuilder), inlineParamDocs);
         }
      }
      needsResolve.forEach(Docs::resolveLazyParams);
      for (Map.Entry<Class<?>, Docs> entry : docs.entrySet()) {
         reverseTypes.put(entry.getValue(), entry.getKey());
      }

      File outputDir = output.toFile();
      if (outputDir.exists()) {
         if (!outputDir.isDirectory()) {
            System.err.println("Output parameter " + output + " must be a folder");
         }
      } else {
         outputDir.mkdirs();
      }
      for (Map.Entry<String, Docs> step : steps.entrySet()) {
         Path filePath = output.resolve("step_" + step.getKey() + ".md");
         try (PrintStream out = new PrintStream(new FileOutputStream(filePath.toFile()))) {
            printFrontMatter(out, step.getValue(), step.getKey());
            printDocs(step.getValue(), out);
         } catch (FileNotFoundException e) {
            System.err.printf("Cannot write file %s: %s%n", filePath, e);
         }
      }
      printRootType("action", Action.Builder.class);
      printRootType("processor", Processor.Builder.class);
   }

   private void printRootType(String type, Class<?> builderClazz) {
      for (Map.Entry<String, List<Docs>> entry : docs.get(builderClazz).params.entrySet()) {
         Path filePath = output.resolve(type + "_" + entry.getKey() + ".md");
         try (PrintStream out = new PrintStream(new FileOutputStream(filePath.toFile()))) {
            Docs docs = entry.getValue().iterator().next();
            printFrontMatter(out, docs, entry.getKey());
            printDocs(docs, out);
         } catch (FileNotFoundException e) {
            System.err.printf("Cannot write file %s: %s%n", filePath, e);
         }
      }
   }

   private void printFrontMatter(PrintStream out, Docs docs, String title) {
      out.println("---");
      if (title == null && docs != null && docs.type != null) {
         title = docs.type;
      }
      if (title != null) {
         out.printf("title: \"%s\"%n", title);
      }
      if (docs != null && docs.ownerDescription != null) {
         out.printf("description: \"%s\"%n", docs.ownerDescription.replaceAll("\"", "\\\\\""));
      }
      out.println("---");
   }

   private void printLink(String type, String name, Docs docs, PrintStream out) {
      String description = docs.ownerDescription;
      if (description == null) {
         out.printf("* [%s](./%s_%s.html)%n", name, type, name);
      } else {
         int endOfLine = description.indexOf('\n');
         if (endOfLine >= 0) {
            description = description.substring(0, endOfLine);
         }
         out.printf("* [%s](./%s_%s.html): %s%n", name, type, name, description);
      }
   }

   private void printDocs(Docs docs, PrintStream out) {
      if (docs.typeDescription != null) {
         out.println(docs.typeDescription);
      }
      if (docs.inlineParam != null) {
         out.println();
         out.println("| Inline definition |\n| -------- |");
         out.printf("| %s |%n", docs.inlineParam);
         out.println();
      }
      if (!docs.params.isEmpty()) {
         List<Tuple> children = new ArrayList<>();
         List<Tuple> processed = new ArrayList<>();
         Set<Docs> found = new HashSet<>();
         for (Map.Entry<String, List<Docs>> param : docs.params.entrySet()) {
            for (Docs d : param.getValue()) {
               if (d.link == null && !d.params.isEmpty() && found.add(d)) {
                  processed.add(new Tuple(param.getKey(), d));
               }
            }
         }
         while (!processed.isEmpty()) {
            children.addAll(processed);
            List<Tuple> newChildren = new ArrayList<>();
            for (Tuple t : children) {
               for (Map.Entry<String, List<Docs>> param : t.docs.params.entrySet()) {
                  for (Docs d : param.getValue()) {
                     if (d.link == null && !d.params.isEmpty() && found.add(d)) {
                        newChildren.add(new Tuple(t.name + "." + param.getKey(), d));
                     }
                  }
               }
            }
            processed = newChildren;
         }
         Map<Docs, String> reverseLookup = new HashMap<>();
         for (Tuple t : children) {
            reverseLookup.put(t.docs, t.name);
         }

         out.println();
         out.println("| Property | Type | Description |\n| ------- | ------- | -------- |");
         for (Map.Entry<String, List<Docs>> param : docs.params.entrySet()) {
            printDocs(param.getKey(), param.getValue(), out, reverseLookup);
         }
         out.println();

         Collections.sort(children, Comparator.comparing(t -> t.name, String.CASE_INSENSITIVE_ORDER));
         for (Tuple t : children) {
            out.printf("### %s%n%n", reverseLookup.get(t.docs), t.name);
            if (t.docs.typeDescription != null) {
               out.println(t.docs.typeDescription);
               out.println();
            }
            if (t.docs.inlineParam != null) {
               out.println();
               out.println("| Inline definition |\n| -------- |");
               out.printf("| %s |%n", t.docs.inlineParam);
               out.println();
            }
            out.println("| Property | Type | Description |\n| ------- | ------- | ------- |");
            for (Map.Entry<String, List<Docs>> param : t.docs.params.entrySet()) {
               printDocs(param.getKey(), param.getValue(), out, reverseLookup);
            }
            out.println();
         }
      }
   }

   private void printDocs(String name, List<Docs> options, PrintStream out, Map<Docs, String> reverseLookup) {
      int printed = 0;
      for (Docs d : options) {
         if (d.ownerDescription == null && d.params.isEmpty()) {
            continue;
         }
         out.printf("| %s ", name);
         if (printed > 0) {
            out.print("(alternative)");
         }
         if (d.link != null) {
            out.printf("| [%s](%s) ", d.type, d.link);
         } else if (d.params.isEmpty()) {
            out.printf("| %s ", d.type);
         } else {
            out.printf("| [%s](#%s) ", d.type, reverseLookup.get(d)
                  .replaceAll("&lt;", "lt").replaceAll("&gt;", "gt").replaceAll(" ", "-").replaceAll("[^a-zA-Z0-9-_]", "").toLowerCase());
         }
         out.printf("| %s |%n", d.ownerDescription == null ? NO_DESCRIPTION : d.ownerDescription);
         ++printed;
      }
      if (printed == 0) {
         out.printf("| %s | &lt;unknown&gt; | %s |%n", name, NO_DESCRIPTION);
      }
   }

   private static String javadocToMarkdown(String text) {
      if (text == null) {
         return null;
      }
      return text.replaceAll("<pre>", "\n```")
            .replaceAll("</pre>", "```\n")
            .replaceAll("\\{@code +([^}]*)\\}", "<code>$1</code>")
            .replaceAll("<p>", "\n\n");
   }

   private String findInlineParamDocs(ClassOrInterfaceDeclaration cd) {
      return cd.findFirst(MethodDeclaration.class, md -> matches(md, "init", String.class))
            .map(md -> javadocToMarkdown(getJavadocParams(md.getJavadoc()).get("param"))).orElse(null);
   }

   private MethodDeclaration findMatching(List<MethodDeclaration> methods, Method method) {
      METHODS:
      for (MethodDeclaration m : methods) {
         int parameterCount = m.getParameters().size();
         if (m.getName().asString().equals(method.getName()) && parameterCount == method.getParameterCount()) {
            for (int i = 0; i < parameterCount; ++i) {
               if (!matches(m.getParameter(i).getType(), method.getParameters()[i].getType())) {
                  continue METHODS;
               }
            }
            return m;
         }
      }
      return null;
   }

   private boolean matches(Type type, Class<?> clazz) {
      if (type instanceof PrimitiveType) {
         return ((PrimitiveType) type).getType().asString().equals(clazz.getName());
      } else if (type instanceof ClassOrInterfaceType) {
         ClassOrInterfaceType classType = (ClassOrInterfaceType) type;
         String fqName = fqName(classType);
         return clazz.getName().endsWith(fqName);
      }
      return false;
   }

   private boolean matches(MethodDeclaration declaration, String name, Class<?>... parameters) {
      if (!declaration.getName().asString().equals(name) || declaration.getParameters().size() != parameters.length) {
         return false;
      }
      for (int i = 0; i < parameters.length; ++i) {
         if (!matches(declaration.getParameter(i).getType(), parameters[i])) {
            return false;
         }
      }
      return true;
   }

   private String fqName(ClassOrInterfaceType type) {
      return type.getScope().map(s -> fqName(s) + ".").orElse("") + type.getNameAsString();
   }

   private CompilationUnit findUnit(Class<?> clazz) {
      while (clazz.getEnclosingClass() != null) {
         clazz = clazz.getEnclosingClass();
      }
      String src = clazz.getName().replaceAll("\\.", File.separator) + ".java";
      File file = sourceDirs.stream().map(path -> path.resolve(src).toFile())
            .filter(f -> f.exists() && f.isFile()).findFirst().orElse(null);
      if (file != null) {
         try {
            ParseResult<CompilationUnit> result = parser.parse(file);
            if (result.isSuccessful()) {
               return result.getResult().orElseThrow(IllegalStateException::new);
            } else {
               System.err.printf("Cannot parse file %s:%n", file);
               for (Problem p : result.getProblems()) {
                  System.err.println(p.getVerboseMessage());
               }
            }
         } catch (FileNotFoundException e) {
            System.err.printf("Cannot read file %s: %s%n", file, e.getMessage());
         }
      }
      if (!clazz.getName().startsWith("java.")) {
         System.err.printf("Cannot find source code for %s%n", clazz);
      }
      return null;
   }

   private ClassOrInterfaceDeclaration findClass(Class<?> builder) {
      Node node = findClassOrEnum(builder, ClassOrInterfaceDeclaration.class);
      if (node == null) return null;
      return (ClassOrInterfaceDeclaration) node;
   }

   private EnumDeclaration findEnum(Class<?> builder) {
      Node node = findClassOrEnum(builder, EnumDeclaration.class);
      if (node == null) return null;
      return (EnumDeclaration) node;
   }

   private <T extends Node & NodeWithSimpleName> Node findClassOrEnum(Class<?> builder, Class<T> type) {
      if (BLACKLIST.contains(builder)) {
         return null;
      }
      Node node = findUnit(builder);
      if (node == null) {
         return null;
      }
      Stack<Class<?>> classes = new Stack<>();
      Class<?> clazz = builder;
      while (clazz != null) {
         classes.push(clazz);
         clazz = clazz.getEnclosingClass();
      }
      while (!classes.isEmpty()) {
         String simpleName = classes.pop().getSimpleName();
         if (classes.isEmpty()) {
            node = node.findFirst(type, cd -> cd.getNameAsString().equals(simpleName)).orElse(null);
         } else {
            node = node.findFirst(ClassOrInterfaceDeclaration.class, cd -> cd.getNameAsString().equals(simpleName)).orElse(null);
         }
         if (node == null) {
            System.err.printf("Cannot describe builder %s%n", builder);
            return null;
         }
      }
      return node;
   }

   private String getJavadocDescription(NodeWithJavadoc<?> declaration) {
      return declaration == null ? null : declaration.getJavadoc()
            .map(javadoc -> trimEmptyLines(javadoc.getDescription().toText()))
            .map(DocsGenerator::javadocToMarkdown)
            .orElse(null);
   }

   private String trimEmptyLines(String description) {
      String[] lines = description.split("\n");
      int firstLine = 0, lastLine = lines.length - 1;
      for (; firstLine < lines.length; ++firstLine) {
         if (!lines[firstLine].trim().isEmpty()) break;
      }
      for (; lastLine >= firstLine; --lastLine) {
         if (!lines[lastLine].trim().isEmpty()) break;
      }
      StringBuilder sb = new StringBuilder();
      boolean preformatted = false;
      for (int i = firstLine; i <= lastLine; ++i) {
         if (lines[i].contains("<pre>")) {
            preformatted = true;
         }
         if (lines[i].contains("</pre>")) {
            preformatted = false;
         }
         if (lines[i].trim().isEmpty()) {
            sb.append("<br>");
         }
         sb.append(lines[i]);
         if (preformatted) {
            sb.append('\n');
         } else if (i != lastLine) {
            sb.append(" ");
         }
      }
      if (sb.length() == 0) {
         return "";
      }
      return sb.toString();
   }

   private Map<String, String> getJavadocParams(Optional<Javadoc> maybeJavadoc) {
      return maybeJavadoc
            .map(javadoc -> javadoc.getBlockTags().stream()
                  .filter(tag -> tag.getType() == JavadocBlockTag.Type.PARAM)
                  .collect(Collectors.toMap(tag -> tag.getName().orElse("<unknown>"), tag -> tag.getContent().toText())))
            .orElse(Collections.emptyMap());
   }

   private void addStep(String name, Class<?> builder, boolean inline, String inlineDocs) {
      Docs step = steps.get(name);
      if (step == null) {
         step = describeBuilder(builder, false);
         step.ownerDescription = firstLine(step.typeDescription);
         steps.put(name, step);
      } else if (step.params.isEmpty()) {
         // The step could have been created from inline-param version in StepCatalog
         Docs docs = describeBuilder(builder, false);
         step.typeDescription = docs.typeDescription;
         step.params.putAll(docs.params);
         if (step.ownerDescription == null) {
            step.ownerDescription = firstLine(step.typeDescription);
         }
      }
      if (step.inlineParam == null && inline) {
         step.inlineParam = inlineDocs;
      }
   }

   private String firstLine(String text) {
      if (text == null) {
         return null;
      }
      text = text.trim();
      int endOfLine = IntStream.of(text.indexOf('\n'), text.indexOf("<br"), text.indexOf("<p>"))
            .filter(index -> index >= 0).min().orElse(-1);
      return endOfLine >= 0 ? text.substring(0, endOfLine) : text;
   }

   private Docs describeBuilder(Class<?> builder, boolean addParamsFromType) {
      if (docs.containsKey(builder)) {
         return docs.get(builder);
      }
      if (builder == ServiceLoadedBuilderProvider.class) {
         throw new IllegalArgumentException();
      }
      ClassOrInterfaceDeclaration cd = findClass(builder);
      if (cd == null) {
         return null;
      }
      Map<Class<?>, List<MethodDeclaration>> methods = new HashMap<>();
      Docs docs = new Docs(null);
      docs.typeDescription = getJavadocDescription(cd);
      if (InitFromParam.class.isAssignableFrom(builder)) {
         docs.inlineParam = findInlineParamDocs(cd);
      }
      this.docs.put(builder, docs);
      if (BaseSequenceBuilder.class.isAssignableFrom(builder)) {
         return docs;
      }
      if (addParamsFromType) {
         addParamsFromBuilders(docs, builder, builder);
      }

      findProperties(builder, m -> {
         List<MethodDeclaration> mds = methods.computeIfAbsent(m.getDeclaringClass(), this::findAllMethods);
         Docs param = describeMethod(m.getDeclaringClass(), m, findMatching(mds, m));
         if (param != null) {
            docs.addParam(m.getName(), param);
         }
      });
      return docs;
   }

   private List<MethodDeclaration> findAllMethods(Class<?> clazz) {
      List<MethodDeclaration> declarations = new ArrayList<>();
      while (clazz != null) {
         ClassOrInterfaceDeclaration cd = findClass(clazz);
         if (cd != null) {
            declarations.addAll(cd.findAll(MethodDeclaration.class));
         }
         clazz = clazz.getSuperclass();
      }
      return declarations;
   }

   private Docs describeMethod(Class<?> builder, Method m, MethodDeclaration declaration) {
      StringBuilder description = declaration == null ? new StringBuilder() : declaration.getJavadoc()
            .map(javadoc -> new StringBuilder(trimEmptyLines(javadoc.getDescription().toText()))).orElse(new StringBuilder());

      // Return early to not recurse into self
      if (m.getReturnType().isAssignableFrom(builder)) {
         String type = "&lt;none&gt;";
         if (m.getParameterCount() == 0) {
            description.append("<br>Note: property does not have any value");
         } else if (m.getParameterCount() == 1) {
            Class<?> singleParam = m.getParameters()[0].getType();
            if (singleParam.isEnum()) {
               type = "enum";
               EnumDeclaration cd = findEnum(singleParam);
               if (cd != null) {
                  List<EnumConstantDeclaration> constants = cd.findAll(EnumConstantDeclaration.class);
                  if (constants != null) {
                     description.append("<br>Options:<ul>");
                     for (EnumConstantDeclaration c : constants) {
                        description.append("<li><code>").append(c.getNameAsString()).append("</code>");
                        String optionDescription = getJavadocDescription(c);
                        if (optionDescription != null) {
                           description.append(optionDescription);
                        }
                        description.append("</li>");
                     }
                     description.append("</ul>");
                  }
               }
            } else {
               type = singleParam.getSimpleName();
            }
         }
         Docs docs = new Docs(description.isEmpty() ? null : description.toString());
         docs.type = type;
         return docs;
      }

      Docs param = new Docs(description.isEmpty() ? null : description.toString());

      if (BaseSequenceBuilder.class.isAssignableFrom(m.getReturnType())) {
         param.addParam("&lt;list of steps&gt;", EMPTY_DOCS);
         param.type = "&lt;list of steps&gt;";
         param.link = "index.html#steps";
      }
      addParamsFromBuilders(param, m.getReturnType(), m.getGenericReturnType());

      if (ServiceLoadedBuilderProvider.class.isAssignableFrom(m.getReturnType())) {
         ParameterizedType returnType = (ParameterizedType) m.getAnnotatedReturnType().getType();
         Class<?> builderClazz = getRawClass(returnType.getActualTypeArguments()[0]);
         setServiceLoaded(param, builderClazz);
      }
      if (m.getReturnType().getName().endsWith("Builder")) {
         Docs inner = describeBuilder(m.getReturnType(), false);
         if (inner != null) {
            param.typeDescription = inner.typeDescription;
            param.inlineParam = inner.inlineParam;
            param.type = "Builder";
            param.lazyParams.add(inner.params);
            needsResolve.add(param);
         }
      }
      if (param.params.isEmpty() && param.lazyParams.isEmpty()) {
         return null;
      } else {
         return param;
      }
   }

   private void setServiceLoaded(Docs param, Class<?> builderClazz) {
      param.type = builderType(builderClazz);
      if (builderClazz == Action.Builder.class) {
         param.link = "index.html#actions";
      } else if (Processor.Builder.class.isAssignableFrom(builderClazz)) {
         param.link = "index.html#processors";
      }
      param.lazyParams.add(getServiceLoadedImplementations(builderClazz).params);
      needsResolve.add(param);
   }

   private void addParamsFromBuilders(Docs docs, Class<?> builder, java.lang.reflect.Type genericType) {
      if (PairBuilder.class.isAssignableFrom(builder)) {
         Docs inner = describeBuilder(builder, false);
         ClassOrInterfaceDeclaration cd = findClass(builder);
         if (cd != null) {
            inner.ownerDescription = firstLine(getMethodJavadoc(cd, "accept", 2));
         }
         docs.type = inner.type = getRawClass(getGenericParams(genericType, PairBuilder.class)[0]).getSimpleName();
         docs.addParam("&lt;any&gt;", inner);
      }
      if (PartialBuilder.class.isAssignableFrom(builder)) {
         try {
            Method withKey = builder.getMethod("withKey", String.class);
            Class<?> innerBuilder = withKey.getReturnType();
            String ownerDescription = null;
            ClassOrInterfaceDeclaration cd = findClass(builder);
            if (cd != null) {
               ownerDescription = getMethodJavadoc(cd, "withKey", 1);
            }
            Docs inner;
            if (innerBuilder == ServiceLoadedBuilderProvider.class) {
               Class<?> implBuilder = getRawClass(((ParameterizedType) withKey.getGenericReturnType()).getActualTypeArguments()[0]);
               inner = new Docs(ownerDescription);
               setServiceLoaded(inner, implBuilder);
            } else {
               inner = describeBuilder(innerBuilder, false);
               inner.ownerDescription = ownerDescription;
            }

            docs.type = inner.type = "Builder";
            docs.addParam("&lt;any&gt;", inner);
         } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
         }
      }

      if (ListBuilder.class.isAssignableFrom(builder)) {
         Docs inner = describeBuilder(builder, false);
         if (inner == null) {
            inner = new Docs(null);
         } else if (inner.ownerDescription == null) {
            inner.ownerDescription = firstLine(inner.typeDescription);
         }
         docs.type = inner.type = "&lt;list of strings&gt;";
         docs.addParam("&lt;list of strings&gt;", inner);
      }
      if (MappingListBuilder.class.isAssignableFrom(builder)) {
         try {
            Docs inner = describeBuilder(builder.getMethod("addItem").getReturnType(), false);
            ClassOrInterfaceDeclaration cd = findClass(builder);
            if (cd != null) {
               inner.ownerDescription = firstLine(getMethodJavadoc(cd, "addItem", 0));
            }
            docs.type = inner.type = "&lt;list of builders&gt;";
            docs.addParam("&lt;list of mappings&gt;", inner);
         } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
         }
      }
   }

   private String getMethodJavadoc(ClassOrInterfaceDeclaration cd, String methodName, int paramCount) {
      return cd.findFirst(MethodDeclaration.class, md -> md.getNameAsString().equals(methodName) && md.getParameters().size() == paramCount)
            .map(this::getJavadocDescription).orElse(null);
   }

   private String builderType(Class<?> builderClazz) {
      if (Processor.Builder.class.isAssignableFrom(builderClazz)) {
         return "Processor.Builder";
      }
      String type = builderClazz.getSimpleName();
      if (builderClazz.getDeclaringClass() != null) {
         type = builderClazz.getDeclaringClass().getSimpleName() + "." + type;
      }
      return type;
   }

   private java.lang.reflect.Type[] getGenericParams(java.lang.reflect.Type type, Class<?> iface) {
      if (type == Object.class || type == null) {
         return null;
      } else if (type instanceof ParameterizedType) {
         Class<?> rawType = (Class<?>) ((ParameterizedType) type).getRawType();
         if (rawType == iface) {
            return ((ParameterizedType) type).getActualTypeArguments();
         }
         return getGenericParams(rawType, iface);
      } else if (type instanceof Class<?>) {
         return getGenericParams((Class<?>) type, iface);
      } else {
         throw new UnsupportedOperationException(type.getTypeName());
      }
   }

   private java.lang.reflect.Type[] getGenericParams(Class<?> rawType, Class<?> iface) {
      java.lang.reflect.Type[] params = getGenericParams(rawType.getGenericSuperclass(), iface);
      if (params != null) {
         return params;
      }
      for (java.lang.reflect.Type i : rawType.getGenericInterfaces()) {
         params = getGenericParams(i, iface);
         if (params != null) {
            return params;
         }
      }
      return null;
   }

   private Docs getServiceLoadedImplementations(Class<?> builderClazz) {
      Docs implementations = docs.get(builderClazz);
      if (implementations != null) {
         return implementations;
      }
      implementations = new Docs(null);
      docs.put(builderClazz, implementations);
      ClassOrInterfaceDeclaration fd = findClass(builderClazz);
      implementations.typeDescription = getJavadocDescription(fd);
      for (Map.Entry<String, BuilderInfo<?>> entry : ServiceLoadedBuilderProvider.builders(builderClazz).entrySet()) {
         Class<?> newBuilder = entry.getValue().implClazz;
         if (newBuilder.isAnnotationPresent(Deprecated.class)) {
            continue;
         }
         Docs docs = describeBuilder(newBuilder, true);
         if (docs == null) {
            continue;
         }
         docs.ownerDescription = firstLine(docs.typeDescription);
         docs.type = builderType(newBuilder);
         implementations.addParam(entry.getKey(), docs);
      }
      return implementations;
   }

   private static class Docs {
      private static final Comparator<? super Docs> DOCS_COMPARATOR = Comparator
            .<Docs, Integer>comparing(d -> d.params.size())
            .thenComparing(d -> d.inlineParam == null ? "" : d.inlineParam)
            .thenComparing(d -> d.typeDescription == null ? "" : d.typeDescription)
            .thenComparing(d -> d.ownerDescription == null ? "" : d.ownerDescription)
            .thenComparing(d -> d.type == null ? "" : d.type);
      String ownerDescription;
      String typeDescription;
      String inlineParam;
      Map<String, List<Docs>> params = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
      String link;
      String type;
      List<Map<String, List<Docs>>> lazyParams = new ArrayList<>();

      private Docs(String ownerDescription) {
         this.ownerDescription = ownerDescription;
         this.typeDescription = ownerDescription;
      }

      public void addParam(String name, Docs docs) {
         List<Docs> options = params.get(name);
         if (options == null) {
            options = new ArrayList<>();
            params.put(name, options);
         }
         options.add(docs);
         options.sort(DOCS_COMPARATOR);
      }

      public void resolveLazyParams() {
         for (Map<String, List<Docs>> params : lazyParams) {
            for (Map.Entry<String, List<Docs>> param : params.entrySet()) {
               for (Docs d : param.getValue()) {
                  addParam(param.getKey(), d);
               }
            }
         }
         lazyParams.clear();
      }
   }

   private static class Tuple {
      final String name;
      final Docs docs;

      private Tuple(String name, Docs docs) {
         this.name = name;
         this.docs = docs;
      }
   }
}
