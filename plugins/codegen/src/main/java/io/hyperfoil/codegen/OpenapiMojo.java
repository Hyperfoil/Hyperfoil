package io.hyperfoil.codegen;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.MavenProject;
import org.apache.maven.plugins.annotations.Parameter;
import org.yaml.snakeyaml.Yaml;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MarkerAnnotationExpr;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.stmt.BlockStmt;

@Mojo(name = "codegen", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class OpenapiMojo extends AbstractMojo {
   private static final String COMPONENTS_SCHEMAS = "#/components/schemas/";

   @Parameter(readonly = true, defaultValue = "${project}")
   private MavenProject project;

   @Parameter(defaultValue = "${project.basedir}/src/main/resources/openapi.yaml")
   private String input;

   @Parameter(defaultValue = "${project.build.directory}/generated-sources/java")
   private String output;

   @Parameter(required = true)
   private String modelPackage;

   @Parameter(required = true)
   private String servicePackage;

   @Parameter(required = true)
   private String routerPackage;

   @Parameter
   private String defaultDateFormat;

   @Parameter(defaultValue = "true")
   private boolean addToCompileRoots;

   @Override
   public void execute() throws MojoExecutionException {
      Yaml yaml = new Yaml();
      Map<String, Object> openapi;
      try (FileInputStream is = new FileInputStream(input)) {
         openapi = yaml.load(is);
      } catch (IOException e) {
         throw new MojoExecutionException("Failed to read " + input, e);
      }
      ensureDir(new File(output), "Output directory");
      ensureDir(Paths.get(output, modelPackage.split("\\.")).toFile(), "Package directory");
      ensureDir(Paths.get(output, servicePackage.split("\\.")).toFile(), "Service directory");
      ensureDir(Paths.get(output, routerPackage.split("\\.")).toFile(), "Router directory");

      generateServiceAndRouter(openapi);
      generateModel(openapi);
   }

   private void generateServiceAndRouter(Map<String, Object> openapi) throws MojoExecutionException {
      ArrayList<Operation> operations = new ArrayList<>();
      Map<String, Object> paths = descend(openapi, false, "paths");
      for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
         String path = pathEntry.getKey();
         @SuppressWarnings("unchecked")
         Map<String, Map<String, Object>> methods = (Map<String, Map<String, Object>>) pathEntry.getValue();
         for (Map.Entry<String, Map<String, Object>> methodEntry : methods.entrySet()) {
            String method = methodEntry.getKey();
            Map<String, Object> properties = methodEntry.getValue();
            String operationId = requireNonNull(properties, "operationId", path + "." + method);
            ArrayList<Param> params = new ArrayList<>();
            Map<String, Object> requestBodyContent = descend(properties, true, "requestBody", "content");
            ArrayList<String> consumes = new ArrayList<>();
            if (requestBodyContent != null) {
               consumes.addAll(requestBodyContent.keySet());
            }
            if (consumes.isEmpty()) {
               consumes.add("");
            }
            ArrayList<String> produces = new ArrayList<>();
            Map<String, Map<String, Object>> responses = requireNonNull(properties, "responses", path + "." + method);
            for (Map<String, Object> response : responses.values()) {
               @SuppressWarnings("unchecked")
               Map<String, Object> content = (Map<String, Object>) response.get("content");
               if (content != null) {
                  produces.addAll(content.keySet());
               }
            }
            if (produces.isEmpty()) {
               produces.add("");
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> parameters = (List<Map<String, Object>>) properties.get("parameters");
            if (parameters != null) {
               for (Map<String, Object> param : parameters) {
                  String name = requireNonNull(param, "name", path, method, "parameters");
                  String in = requireNonNull(param, "in", path, method, "parameters");
                  boolean required = "true".equalsIgnoreCase(String.valueOf(param.get("required")));
                  Map<String, Object> schema = requireNonNull(param, "schema", path, method, name);
                  Property property = createProperty(path + "." + method + ".", name, schema);
                  Object defaultValue = schema.get("default");
                  params.add(new Param(property.originalName, property.fieldName, in, property.type, required, defaultValue == null ? null : String.valueOf(defaultValue)));
               }
            }
            for (String consume : consumes) {
               for (String produce : produces) {
                  operations.add(new Operation(path, method, operationId, consume, produce, consumes.size(), produces.size(), params));
               }
            }
         }
      }

      writeApiService(operations);
      writeApiRouter(operations);
   }

   private void writeApiRouter(ArrayList<Operation> operations) throws MojoExecutionException {
      CompilationUnit unit = new CompilationUnit(routerPackage);
      unit.addImport("java.util.Date");
      unit.addImport("java.util.List");
      unit.addImport("java.util.Map");
      unit.addImport("java.util.Collections");
      unit.addImport("io.vertx.core.json.Json");
      unit.addImport("io.vertx.ext.web.handler.BodyHandler");
      unit.addImport("io.vertx.ext.web.Router");
      unit.addImport("io.vertx.ext.web.RoutingContext");
      unit.addImport("org.apache.logging.log4j.Logger");
      unit.addImport("org.apache.logging.log4j.LogManager");
      unit.addImport(modelPackage, false, true);
      unit.addImport(servicePackage + ".ApiService");

      ClassOrInterfaceDeclaration clazz = unit.addClass("ApiRouter", Modifier.Keyword.PUBLIC);
      clazz.addField("ApiService", "service", Modifier.Keyword.PRIVATE, Modifier.Keyword.FINAL);
      clazz.addField("Logger", "log", Modifier.Keyword.PRIVATE, Modifier.Keyword.STATIC, Modifier.Keyword.FINAL)
            .getVariable(0).setInitializer("LogManager.getLogger(ApiRouter.class)");
      ConstructorDeclaration ctor = clazz.addConstructor(Modifier.Keyword.PUBLIC);
      BlockStmt ctorBody = ctor.addParameter("ApiService", "service").addParameter("Router", "router").getBody();
      ctorBody.addStatement("this.service = service;");
      ctorBody.addStatement("router.route().handler(BodyHandler.create(System.getProperty(\"java.io.tmpdir\")));");
      ctorBody.addStatement("router.errorHandler(500, ctx -> {\n" +
            "            log.error(\"Error processing {} {}\", ctx.request().method(), ctx.request().uri(), ctx.failure());\n" +
            "        });");
      for (Operation operation : operations) {
         StringBuilder routing = new StringBuilder("router.").append(operation.method).append("(\"")
               .append(operation.path.replaceAll("\\{", ":").replaceAll("\\}", ""))
               .append("\")");
         if (!operation.consumes.isEmpty()) {
            routing.append(".consumes(\"").append(operation.consumes).append("\")");
         }
         if (!operation.produces.isEmpty()) {
            routing.append(".produces(\"").append(operation.produces).append("\")");
         }
         routing.append(".handler(this::").append(operation.name()).append(");");
         ctorBody.addStatement(routing.toString());
      }
      for (Operation operation : operations) {
         MethodDeclaration method = clazz.addMethod(operation.name(), Modifier.Keyword.PRIVATE);
         method.addParameter("RoutingContext", "ctx");
         BlockStmt body = new BlockStmt();
         method.setBody(body);
         StringBuilder invocation = new StringBuilder("service.").append(operation.name()).append("(ctx");
         for (Param param : operation.params) {
            StringBuilder raw = new StringBuilder().append("String _").append(param.varName).append(" = ")
                  .append(param.in).append("Param(ctx, \"").append(param.originalName).append("\", ");
            if (param.defaultValue == null) {
               raw.append("null");
            } else {
               raw.append('"').append(param.defaultValue).append('"');
            }
            raw.append(");");
            body.addStatement(raw.toString());
            if (param.required) {
               body.addStatement("if (_" + param.varName + " == null) {" +
                     "ctx.response().setStatusCode(400).end(\"" + param.in + " parameter '" + param.originalName + "' was not set!\");" +
                     "return; }");
            }
            body.addStatement(new StringBuilder().append(param.type).append(" ").append(param.varName)
                  .append(" = convert(_").append(param.varName).append(", ").append(param.type.replaceAll("<.*>", "")).append(".class);").toString());
            invocation.append(", ").append(param.varName);
         }
         invocation.append(");");
         body.addStatement(invocation.toString());
      }

      MethodDeclaration pathParam = clazz.addMethod("pathParam", Modifier.Keyword.PRIVATE).setType("String")
            .addParameter("RoutingContext", "ctx")
            .addParameter("String", "name")
            .addParameter("String", "defaultValue");
      pathParam.setBody(new BlockStmt().addStatement("return ctx.pathParam(name);"));

      MethodDeclaration queryParam = clazz.addMethod("queryParam", Modifier.Keyword.PRIVATE).setType("String")
            .addParameter("RoutingContext", "ctx")
            .addParameter("String", "name")
            .addParameter("String", "defaultValue");
      BlockStmt queryParamBody = new BlockStmt()
            .addStatement("List<String> list = ctx.queryParam(name);")
            .addStatement("if (list == null || list.isEmpty()) return defaultValue;")
            .addStatement("return list.iterator().next();");
      queryParam.setBody(queryParamBody);

      MethodDeclaration headerParam = clazz.addMethod("headerParam", Modifier.Keyword.PRIVATE).setType("String")
            .addParameter("RoutingContext", "ctx")
            .addParameter("String", "name")
            .addParameter("String", "defaultValue");
      headerParam.setBody(new BlockStmt()
            .addStatement("String value = ctx.request().getHeader(name);")
            .addStatement("return value == null ? defaultValue : value;"));

      MethodDeclaration convert = clazz.addMethod("convert");
      convert.addAnnotation(new SingleMemberAnnotationExpr(new Name("SuppressWarnings"), new StringLiteralExpr("unchecked")));
      convert.addParameter("String", "value").addParameter("Class<T>", "type").addTypeParameter("T").setType("T");
      BlockStmt convertBody = new BlockStmt();
      convert.setBody(convertBody);
      convertBody.addStatement("if (type == String.class) return (T) value;");
      convertBody.addStatement("if (type == boolean.class) return (T) Boolean.valueOf(value);");
      convertBody.addStatement("if (type == int.class) return (T) Integer.valueOf(value);");
      convertBody.addStatement("if (type == List.class) { if (value == null) return (T) Collections.emptyList(); if (value instanceof String) return (T) Collections.singletonList(value); }");
      convertBody.addStatement("if (value == null) { if (type == Map.class) return (T) Collections.emptyMap(); return null; }");
      convertBody.addStatement("return Json.decodeValue(value, type);");

      writeUnit(unit, routerPackage, "ApiRouter.java");
   }

   private void writeApiService(ArrayList<Operation> operations) throws MojoExecutionException {
      CompilationUnit unit = new CompilationUnit(servicePackage);
      unit.addImport(modelPackage, false, true);
      unit.addImport("io.vertx.ext.web.RoutingContext");
      unit.addImport("java.util", false, true);
      ClassOrInterfaceDeclaration clazz = unit.addClass("ApiService");
      clazz.setInterface(true);
      for (Operation operation : operations) {
         MethodDeclaration method = clazz.addMethod(operation.name());
         method.setBody(null);
         method.addParameter("RoutingContext", "ctx");
         for (Param param : operation.params) {
            method.addParameter(param.type, param.varName);
         }
      }
      writeUnit(unit, servicePackage, "ApiService.java");
   }

   private void writeUnit(CompilationUnit unit, String pkg, String className) throws MojoExecutionException {
      Path apiServicePath = Paths.get(output, pkg.split("\\.")).resolve(className);
      try {
         Files.write(apiServicePath, unit.toString().getBytes(StandardCharsets.UTF_8));
      } catch (IOException e) {
         throw new MojoExecutionException("Cannot write file " + apiServicePath, e);
      }
   }

   private <T> T requireNonNull(Map<String, Object> map, String key, String... where) throws MojoExecutionException {
      @SuppressWarnings("unchecked")
      T value = (T) map.get(key);
      if (value == null) {
         throw new MojoExecutionException(String.join(".", where) + " missing '" + key + "'");
      }
      return value;
   }

   private void generateModel(Map<String, Object> openapi) throws MojoExecutionException {
      Map<String, Object> schemas = descend(openapi, false, "components", "schemas");
      for (Map.Entry<String, Object> entry : schemas.entrySet()) {
         @SuppressWarnings("unchecked")
         Map<String, Object> value = (Map<String, Object>) entry.getValue();
         Object type = value.get("type");
         if (!"object".equals(type)) {
            throw new MojoExecutionException(entry.getKey() + " is not of type 'object': " + type);
         }
         @SuppressWarnings("unchecked")
         Map<String, Object> properties = (Map<String, Object>) value.get("properties");
         if (properties == null) {
            throw new MojoExecutionException(entry.getKey() + " does not have defined 'properties'");
         }
         generateType(entry.getKey(), properties);
      }

      if (addToCompileRoots) {
         project.addCompileSourceRoot(output);
      }
   }

   private Map<String, Object> descend(Map<String, Object> root, boolean allowMissing, String... path) throws MojoExecutionException {
      Map<String, Object> target = root;
      for (String element : path) {
         @SuppressWarnings("unchecked")
         Map<String, Object> value = (Map<String, Object>) target.get(element);
         if (value == null) {
            if (allowMissing) {
               return null;
            } else {
               throw new MojoExecutionException("Cannot descend path " + String.join(".", path));
            }
         }
         target = value;
      }
      return target;
   }

   private void ensureDir(File file, String description) throws MojoExecutionException {
      if (file.exists()) {
         if (!file.isDirectory()) {
            throw new MojoExecutionException(description + " " + file + " is not a directory.");
         }
      } else if (!file.mkdirs()) {
         throw new MojoExecutionException("Cannot create directory " + file);
      }
   }

   private void generateType(String name, Map<String, Object> propertyMap) throws MojoExecutionException {
      CompilationUnit unit = new CompilationUnit(modelPackage);
      unit.addImport("java.util.Date");
      unit.addImport("java.util.List");
      unit.addImport("com.fasterxml.jackson.annotation.JsonCreator");
      unit.addImport("com.fasterxml.jackson.annotation.JsonFormat");
      unit.addImport("com.fasterxml.jackson.annotation.JsonInclude");
      unit.addImport("com.fasterxml.jackson.annotation.JsonProperty");

      ClassOrInterfaceDeclaration clazz = unit.addClass(name, Modifier.Keyword.PUBLIC);
      List<Property> properties = propertyMap.entrySet().stream()
            .map(e -> {
               @SuppressWarnings("unchecked")
               Map<String, Object> from = (Map<String, Object>) e.getValue();
               return createProperty(name, e.getKey(), from);
            })
            .collect(Collectors.toList());
      for (Property property : properties) {
         FieldDeclaration fieldDeclaration = clazz.addField(property.type, property.fieldName, Modifier.Keyword.PUBLIC, Modifier.Keyword.FINAL);
         property.fieldAnnotations.forEach(fieldDeclaration::addAnnotation);
      }
      ConstructorDeclaration ctor = clazz.addConstructor(Modifier.Keyword.PUBLIC);
      ctor.addAnnotation(new MarkerAnnotationExpr("JsonCreator"));
      BlockStmt ctorBody = new BlockStmt();
      for (Property property : properties) {
         com.github.javaparser.ast.body.Parameter p = new com.github.javaparser.ast.body.Parameter(StaticJavaParser.parseType(property.type), property.fieldName);
         p.addAnnotation(new SingleMemberAnnotationExpr(new Name("JsonProperty"), new StringLiteralExpr(property.fieldName)));
         ctor.addParameter(p);
         ctorBody.addStatement("this." + property.fieldName + " = " + property.fieldName + ";");
      }
      ctor.setBody(ctorBody);

      writeUnit(unit, modelPackage, name + ".java");
   }

   private Property createProperty(String name, String propertyName, Map<String, Object> from) {
      String ref = (String) from.get("$ref");
      if (from.get("type") == null && ref == null) {
         throw fail(name, propertyName, "Either 'type' or '$ref' must be defined.");
      }
      if (ref != null) {
         if (!ref.startsWith(COMPONENTS_SCHEMAS)) {
            throw fail(name, propertyName, "Invalid reference to " + ref + " (should start with " + COMPONENTS_SCHEMAS + ")");
         }
         return new Property(propertyName, sanitizeProperty(propertyName), ref.substring(COMPONENTS_SCHEMAS.length()), Collections.emptyList());
      }
      String type = (String) from.get("type");
      String propertyType;
      String format = (String) from.get("format");
      ArrayList<AnnotationExpr> fieldAnnotations = new ArrayList<>();
      switch (type) {
         case "string":
            if (format == null) {
               propertyType = "String";
            } else if (format.equals("date-time")) {
               propertyType = "Date";
               if (defaultDateFormat != null) {
                  fieldAnnotations.add(new NormalAnnotationExpr()
                        .addPair("shape", "JsonFormat.Shape.STRING")
                        .addPair("pattern", new StringLiteralExpr(defaultDateFormat))
                        .setName("JsonFormat"));
               }
            } else {
               throw fail(name, propertyName, "Unknown string format " + format);
            }
            break;
         case "array":
            @SuppressWarnings("unchecked")
            Map<String, Object> items = (Map<String, Object>) from.get("items");
            if (items == null) {
               throw fail(name, propertyName, "Missing 'items'");
            }
            Property item = createProperty(name, propertyName + ".items", items);
            propertyType = "List<" + item.type + ">";
            break;
         case "boolean":
            propertyType = "boolean";
            break;
         case "integer":
         case "number":
            propertyType = format == null ? "int" : format;
            break;
         case "object":
            String externalType = (String) from.get("x-type");
            if (externalType == null) {
               throw fail(name, propertyName, "Nested objects are not supported; use $ref.");
            } else {
               propertyType = externalType;
               break;
            }
         default:
            throw fail(name, propertyName, "Unknown type " + type);
      }
      String jsonInclude = (String) from.get("x-json-include");
      if (jsonInclude != null) {
         fieldAnnotations.add(new SingleMemberAnnotationExpr(new Name("JsonInclude"), new NameExpr("JsonInclude.Include." + jsonInclude)));
      }
      return new Property(propertyName, sanitizeProperty(propertyName), propertyType, fieldAnnotations);
   }

   private String sanitizeProperty(String name) {
      StringBuilder sb = new StringBuilder();
      boolean upperCase = false;
      for (int i = 0; i < name.length(); ++i) {
         char c = name.charAt(i);
         if (Character.isAlphabetic(c) || Character.isDigit(c)) {
            sb.append(upperCase ? Character.toUpperCase(c) : c);
            upperCase = false;
         } else if (c == '-') {
            upperCase = true;
         } else {
            sb.append('_');
            upperCase = false;
         }
      }
      return sb.toString();
   }

   private RuntimeException fail(String name, String propertyName, String msg) {
      return new RuntimeException(new MojoExecutionException(name + "." + propertyName + ": " + msg));
   }

   private static class Property {
      private final String originalName;
      private final String fieldName;
      private final String type;
      private final List<AnnotationExpr> fieldAnnotations;

      private Property(String originalName, String fieldName, String type, List<AnnotationExpr> fieldAnnotations) {
         this.originalName = originalName;
         this.fieldName = fieldName;
         this.type = type;
         this.fieldAnnotations = fieldAnnotations;
      }
   }

   private static class Operation {
      private final String path;
      private final String method;
      private final String operationId;
      private final String consumes;
      private final String produces;
      private final int numConsumes;
      private final int numProduces;
      private final List<Param> params;
      private final String methodName;

      private Operation(String path, String method, String operationId, String consumes, String produces, int numConsumes, int numProduces, List<Param> params) {
         this.path = path;
         this.method = method;
         this.operationId = operationId;
         this.consumes = consumes;
         this.produces = produces;
         this.numConsumes = numConsumes;
         this.numProduces = numProduces;
         this.params = params;
         this.methodName = operationId + (numConsumes > 1 ? "$" + consumes.replaceAll("[-./*]", "_") : "") + (numProduces > 1 ? "$" + produces.replaceAll("[-./*]", "_") : "");
      }

      public String name() {
         return methodName;
      }
   }

   private static class Param {
      private final String originalName;
      private final String varName;
      private final String in;
      private final String type;
      private final boolean required;
      private final String defaultValue;

      private Param(String originalName, String varName, String in, String type, boolean required, String defaultValue) {
         this.originalName = originalName;
         this.varName = varName;
         this.in = in;
         this.type = type;
         this.required = required;
         this.defaultValue = defaultValue;
      }
   }
}
