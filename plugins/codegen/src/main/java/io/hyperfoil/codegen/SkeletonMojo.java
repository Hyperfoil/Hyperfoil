package io.hyperfoil.codegen;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.kohsuke.MetaInfServices;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Modifier.Keyword;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.type.ReferenceType;
import com.github.javaparser.ast.type.TypeParameter;
import com.github.javaparser.ast.type.VoidType;

import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.config.RunHook;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.config.StepBuilder;
import io.hyperfoil.api.processor.Processor;
import io.hyperfoil.api.processor.RawBytesHandler;
import io.hyperfoil.api.session.Action;
import io.hyperfoil.http.api.HeaderHandler;
import io.hyperfoil.http.api.StatusHandler;

@Mojo(name = "skeleton", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class SkeletonMojo extends AbstractMojo {
   private static final Map<String, SkeletonType> TYPES = new HashMap<>();

   @Parameter(defaultValue = "${project.basedir}/src/main/java")
   private String output;

   @Parameter(alias = "package", property = "skeleton.package")
   private String pkg;

   @Parameter(property = "skeleton.name")
   private String name;

   @Parameter(property = "skeleton.type")
   private String type;

   private BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

   static {
      TYPES.put("step", new SkeletonType("Step", Step.class, StepBuilder.class));
      TYPES.put("action", new SkeletonType("Action", Action.class, Action.Builder.class));
      TYPES.put("requestprocessor", new SkeletonType("Processor", Processor.class, Processor.Builder.class));
      TYPES.put("headerhandler", new SkeletonType("HeaderHandler", HeaderHandler.class, HeaderHandler.Builder.class));
      TYPES.put("statushandler", new SkeletonType("StatusHandler", StatusHandler.class, StatusHandler.Builder.class));
      TYPES.put("rawbyteshandler", new SkeletonType("RawBytesHandler", RawBytesHandler.class, RawBytesHandler.Builder.class));
      TYPES.put("hook", new SkeletonType("Hook", RunHook.class, RunHook.Builder.class));
   }

   private String clazzName;
   private Map<String, Type> genericMapping = Collections.emptyMap();
   private CompilationUnit unit;

   @Override
   public void execute() throws MojoExecutionException, MojoFailureException {
      try {
         pkg = checkPropertyInteractive("Package", pkg);
         name = checkPropertyInteractive("Name", name);
         do {
            type = checkPropertyInteractive("Type (one of " + TYPES.keySet() + ")", type);
            type = type.toLowerCase(Locale.US);
         } while (!TYPES.containsKey(type));
      } catch (IOException e) {
         throw new MojoFailureException("Cannot read input parameters", e);
      }
      File pkgDir = Paths.get(output).resolve(pkg.replaceAll("\\.", File.separator)).toFile();
      if (pkgDir.exists()) {
         if (!pkgDir.isDirectory()) {
            throw new MojoFailureException(pkgDir + " is not a directory.");
         }
      } else if (!pkgDir.mkdirs()) {
         throw new MojoExecutionException("Cannot create " + pkgDir);
      }
      SkeletonType st = TYPES.get(type);
      assert st != null;
      if (st.iface instanceof ParameterizedTypeImpl) {
         genericMapping = ((ParameterizedTypeImpl) st.iface).arguments;
      }

      unit = new CompilationUnit(pkg);
      unit.addImport(Name.class);
      unit.addImport(MetaInfServices.class);

      clazzName = Character.toUpperCase(name.charAt(0)) + name.substring(1) + st.suffix;
      ClassOrInterfaceDeclaration clazz = unit.addClass(clazzName);
      setExtendsOrImplements(clazz, st.iface);
      stubMethods(st.iface, clazz, new HashSet<>());

      ClassOrInterfaceDeclaration builder = new ClassOrInterfaceDeclaration();
      setExtendsOrImplements(builder, st.builder);
      builder.addAnnotation(new SingleMemberAnnotationExpr(new com.github.javaparser.ast.expr.Name("MetaInfServices"),
            new ClassExpr(getClassOrInterfaceType(st.builder))))
            .addAnnotation(
                  new SingleMemberAnnotationExpr(new com.github.javaparser.ast.expr.Name("Name"), new StringLiteralExpr(name)));
      clazz.addMember(builder.setName("Builder").setModifiers(Keyword.PUBLIC, Keyword.STATIC));
      stubMethods(st.builder, builder, new HashSet<>());

      unit.getImports().sort(Comparator.comparing(ImportDeclaration::toString));

      Path javaFile = pkgDir.toPath().resolve(clazzName + ".java");
      try {
         Files.write(javaFile, unit.toString().getBytes(StandardCharsets.UTF_8));
      } catch (IOException e) {
         throw new MojoExecutionException("Cannot write to file " + javaFile, e);
      }
   }

   private void setExtendsOrImplements(ClassOrInterfaceDeclaration clazz, Type supertype) {
      if (getRaw(supertype).isInterface()) {
         clazz.addImplementedType(getClassOrInterfaceType(supertype));
      } else {
         clazz.addExtendedType(getClassOrInterfaceType(supertype));
      }
   }

   private void stubMethods(Type type, ClassOrInterfaceDeclaration clazz, Set<String> implementedMethods) {
      if (type == null) {
         return;
      }
      Class<?> raw = getRaw(type);
      if (raw == Object.class) {
         return;
      }
      for (Method m : raw.getDeclaredMethods()) {
         if (m.isDefault() || m.isSynthetic() || m.isBridge() || Modifier.isStatic(m.getModifiers())) {
            continue;
         } else if (!Modifier.isAbstract(m.getModifiers())) {
            // crude without signature
            implementedMethods.add(m.getName());
            continue;
         } else if (implementedMethods.contains(m.getName())) {
            continue;
         }
         MethodDeclaration method = clazz.addMethod(m.getName(), Keyword.PUBLIC);
         if (m.getName().equals("build") && m.getReturnType() != List.class) {
            method.setType(new ClassOrInterfaceType(null, clazzName));
         } else {
            method.setType(getType(m.getGenericReturnType()));
         }
         method.addMarkerAnnotation("Override");
         Type[] genericParameterTypes = m.getGenericParameterTypes();
         Set<String> paramNames = new HashSet<>();
         for (int i = 0; i < genericParameterTypes.length; i++) {
            Type param = genericParameterTypes[i];
            method.addParameter(getType(param), paramName(param, paramNames));
         }
         BlockStmt body = new BlockStmt();
         method.setBody(body);
         if (m.getReturnType() == boolean.class) {
            body.addStatement("return false;");
         } else if (m.getReturnType() == int.class || m.getReturnType() == long.class) {
            body.addStatement("return 0;");
         } else if (m.getReturnType() != void.class) {
            body.addStatement("return null;");
         }
         implementedMethods.add(m.getName());
      }
      stubMethods(raw.getSuperclass(), clazz, implementedMethods);
      for (Class<?> iface : raw.getInterfaces()) {
         stubMethods(iface, clazz, implementedMethods);
      }
   }

   private Class<?> getRaw(Type type) {
      Class<?> raw;
      if (type instanceof Class) {
         raw = (Class<?>) type;
      } else if (type instanceof ParameterizedType) {
         raw = (Class<?>) ((ParameterizedType) type).getRawType();
      } else {
         throw new IllegalStateException();
      }
      return raw;
   }

   private String paramName(Type param, Set<String> names) {
      String name;
      if (param instanceof Class && ((Class) param).isPrimitive()) {
         name = String.valueOf(param.getTypeName().charAt(0));
      } else {
         String tname = param.getTypeName();
         int genericsStart = tname.indexOf('<');
         if (genericsStart > 0) {
            tname = tname.substring(0, genericsStart);
         }
         int lastDot = tname.lastIndexOf('.');
         int lastDollar = tname.lastIndexOf('$');
         if (lastDot > 0 || lastDollar > 0) {
            tname = tname.substring(Math.max(lastDot, lastDollar) + 1);
         }
         name = Character.toLowerCase(tname.charAt(0)) + tname.substring(1);
      }
      while (!names.add(name)) {
         int lastDigit = name.length() - 1;
         while (lastDigit >= 0 && Character.isDigit(name.charAt(lastDigit))) {
            --lastDigit;
         }
         lastDigit++;
         if (lastDigit < name.length()) {
            int suffix = Integer.parseInt(name.substring(lastDigit));
            name = name.substring(0, lastDigit) + suffix;
         } else {
            name = name + "2";
         }
      }
      return name;
   }

   private ClassOrInterfaceType getClassOrInterfaceType(Type type) {
      if (type instanceof Class) {
         ClassOrInterfaceType iface = StaticJavaParser.parseClassOrInterfaceType(type.getTypeName().replaceAll("\\$", "."));

         ClassOrInterfaceType parentScope = iface.getScope().orElse(null);
         if (parentScope != null && Character.isUpperCase(parentScope.getName().asString().charAt(0))) {
            addImport(parentScope);
            parentScope.removeScope();
         } else {
            addImport(iface);
            iface.removeScope();
         }
         return iface;
      } else if (type instanceof ParameterizedType) {
         ClassOrInterfaceType iface = StaticJavaParser
               .parseClassOrInterfaceType(((ParameterizedType) type).getRawType().getTypeName());
         addImport(iface);
         com.github.javaparser.ast.type.Type[] args = Stream.of(((ParameterizedType) type).getActualTypeArguments())
               .map(t -> getType(t)).toArray(com.github.javaparser.ast.type.Type[]::new);
         iface.setTypeArguments(args);
         return iface.removeScope();
      } else {
         throw new IllegalStateException("Unexpected type " + type);
      }
   }

   private void addImport(ClassOrInterfaceType iface) {
      if (!iface.getScope().map(ClassOrInterfaceType::asString).orElse("").equals("java.lang")) {
         unit.addImport(iface.asString());
      }
   }

   private com.github.javaparser.ast.type.Type getType(Type type) {
      if (type instanceof Class) {
         if (((Class<?>) type).isPrimitive()) {
            if (type == void.class) {
               return new VoidType();
            }
            PrimitiveType.Primitive primitive = Stream.of(PrimitiveType.Primitive.values())
                  .filter(p -> p.name().toLowerCase().equals(type.getTypeName()))
                  .findFirst().orElseThrow(() -> new IllegalStateException("No primitive for " + type));
            return new PrimitiveType(primitive);
         }
      } else if (type instanceof TypeVariable) {
         String name = ((TypeVariable<?>) type).getName();
         Type actual = genericMapping.get(name);
         if (actual != null) {
            return getType(actual);
         }
         return new TypeParameter(name);
      } else if (type instanceof WildcardType) {
         return new com.github.javaparser.ast.type.WildcardType()
               .setSuperType(getBound(((WildcardType) type).getLowerBounds()))
               .setExtendedType(getBound(((WildcardType) type).getUpperBounds()));
      }
      return getClassOrInterfaceType(type);
   }

   private com.github.javaparser.ast.type.ReferenceType getBound(Type[] types) {
      if (types.length == 0) {
         return null;
      } else if (types.length == 1) {
         return (ReferenceType) getType(types[0]);
      } else {
         throw new UnsupportedOperationException();
      }
   }

   private void addImport(Type type) {
      if (type instanceof ParameterizedType) {
         addImport(((ParameterizedType) type).getRawType());
         for (Type arg : ((ParameterizedType) type).getActualTypeArguments()) {
            addImport(arg);
         }
      } else {
         unit.addImport(type.getTypeName());
      }
   }

   private String checkPropertyInteractive(String name, String value) throws IOException {
      while (value == null || value.isEmpty()) {
         System.out.print(name + ": ");
         System.out.flush();
         value = reader.readLine();
      }
      return value;
   }

   private static class SkeletonType {
      private final Type iface;
      private final Class<?> builder;
      private final String suffix;

      private SkeletonType(String suffix, Type iface, Class<?> builder, String... blacklistedMethods) {
         this.suffix = suffix;
         this.iface = iface;
         this.builder = builder;
      }
   }

   private static class ParameterizedTypeImpl implements ParameterizedType {
      private final Class<?> raw;
      private final LinkedHashMap<String, Type> arguments;

      private ParameterizedTypeImpl(Class<?> raw, LinkedHashMap<String, Type> arguments) {
         this.raw = raw;
         this.arguments = arguments;
      }

      @Override
      public Type[] getActualTypeArguments() {
         return arguments.values().toArray(new Type[0]);
      }

      @Override
      public Type getRawType() {
         return raw;
      }

      @Override
      public Type getOwnerType() {
         return null;
      }
   }

   private static class MapBuilder<K, V> {
      private final LinkedHashMap<K, V> map = new LinkedHashMap<>();

      public MapBuilder<K, V> add(K key, V value) {
         map.put(key, value);
         return this;
      }

      public LinkedHashMap<K, V> map() {
         return map;
      }
   }
}
