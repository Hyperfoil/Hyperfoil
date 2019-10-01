package io.hyperfoil.schema;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Map;
import java.util.regex.Pattern;

import io.hyperfoil.api.config.InitFromParam;
import io.hyperfoil.api.config.ListBuilder;
import io.hyperfoil.api.config.MappingListBuilder;
import io.hyperfoil.api.config.PairBuilder;
import io.hyperfoil.api.config.PartialBuilder;
import io.hyperfoil.api.config.BaseSequenceBuilder;
import io.hyperfoil.api.config.StepBuilder;
import io.hyperfoil.core.builders.BuilderInfo;
import io.hyperfoil.core.builders.StepCatalog;
import io.hyperfoil.core.builders.ServiceLoadedBuilderProvider;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class Generator extends BaseGenerator {

   private static final Pattern END_REGEXP = Pattern.compile("^end(\\p{javaUpperCase}.*|$)");
   private static final JsonObject TYPE_NULL = new JsonObject().put("type", "null");
   private static final JsonObject TYPE_STRING = new JsonObject().put("type", "string");

   private final Path input;
   private final Path output;
   private JsonObject definitions;

   public static void main(String[] args) throws IOException {
      new Generator(Paths.get(args[0]), Paths.get(args[1])).run();
   }

   private Generator(Path input, Path output) {
      this.input = input;
      this.output = output;

   }

   private void run() throws IOException {
      String template = Files.readAllLines(input).stream()
            .reduce(new StringBuilder(), StringBuilder::append, StringBuilder::append).toString();
      JsonObject schema = new JsonObject(template);
      definitions = schema.getJsonObject("definitions");
      JsonObject step = definitions.getJsonObject("step");
      JsonArray oneOf = step.getJsonArray("oneOf");
      JsonObject builders = oneOf.getJsonObject(0).getJsonObject("properties");
      JsonArray simpleBuilders = oneOf.getJsonObject(1).getJsonArray("enum");
      simpleBuilders.clear();

      for (Method method : StepCatalog.class.getMethods()) {
         if (StepBuilder.class.isAssignableFrom(method.getReturnType())) {
            addBuilder(builders, simpleBuilders, method.getName(), method.getReturnType(), false);
         } else if (BaseSequenceBuilder.class.isAssignableFrom(method.getReturnType())) {
            addSimpleBuilder(builders, simpleBuilders, method);
         }
      }
      for (Map.Entry<String, BuilderInfo<?>> entry : ServiceLoadedBuilderProvider.builders(StepBuilder.class).entrySet()) {
         Class<StepBuilder> implClazz = (Class<StepBuilder>) entry.getValue().implClazz;
         addBuilder(builders, simpleBuilders, entry.getKey(), implClazz, InitFromParam.class.isAssignableFrom(implClazz));
      }

      if (simpleBuilders.size() == 0) {
         oneOf.remove(1);
      }

      Files.write(output, schema.encodePrettily().getBytes(StandardCharsets.UTF_8));
   }

   private void addSimpleBuilder(JsonObject builders, JsonArray simpleBuilders, Method method) {
      if (method.getParameterCount() == 0) {
         simpleBuilders.add(method.getName());
         addProperty(builders, method.getName(), new JsonObject().put("type", "null"));
      } else if (method.getParameterCount() == 1) {
         addProperty(builders, method.getName(), getType(method));
      }
   }

   private void addBuilder(JsonObject builders, JsonArray simpleBuilders, String name, Class<?> builder, boolean inline) {
      JsonObject properties = new JsonObject();
      if (definitions.getJsonObject(builder.getName()) == null) {
         JsonObject step = new JsonObject();
         definitions.put(builder.getName(), step);
         describeBuilder(builder, step, properties);
         if (properties.size() == 0) {
            simpleBuilders.add(name);
         }
      }
      JsonObject reference = new JsonObject().put("$ref", "#/definitions/" + builder.getName());
      addProperty(builders, name, reference);
      if (inline) {
         addProperty(builders, name, TYPE_STRING);
      }
   }

   private JsonObject describeBuilder(Class<?> builder) {
      if (definitions.getJsonObject(builder.getName()) == null) {
         JsonObject definition = new JsonObject();
         definitions.put(builder.getName(), definition);
         describeBuilder(builder, definition, new JsonObject());
      }
      return new JsonObject().put("$ref", "#/definitions/" + builder.getName());
   }

   private void describeBuilder(Class<?> builder, JsonObject step, JsonObject properties) {
      step.put("type", "object");
      step.put("additionalProperties", false);
      step.put("properties", properties);
      for (Method m : builder.getMethods()) {
         if (Modifier.isStatic(m.getModifiers()) || m.isDefault()) {
            continue;
         } else if (END_REGEXP.matcher(m.getName()).matches()) {
            continue; // do not go up
         } else if (PairBuilder.class.isAssignableFrom(builder) && m.getName().equals("accept") && m.getParameterCount() == 2) {
            continue;
         } else if (ListBuilder.class.isAssignableFrom(builder) && m.getName().equals("nextItem") && m.getParameterCount() == 1) {
            continue;
         } else if (MappingListBuilder.class.isAssignableFrom(builder) && m.getName().equals("addItem") && m.getParameterCount() == 0) {
            continue;
         }
         JsonObject property = describeMethod(builder, m);
         if (property != null) {
            addProperty(properties, m.getName(), property);
         }
      }
   }

   private JsonObject describeMethod(Class<?> builder, Method m) {
      if (m.getReturnType().isAssignableFrom(builder)) {
         if (m.getParameterCount() == 0) {
            return TYPE_NULL;
         } else if (m.getParameterCount() == 1) {
            return getType(m);
         } else {
            // TODO: we could allow passing lists here
            return null;
         }
      }
      ArrayList<JsonObject> options = new ArrayList<>();
      if (PairBuilder.class.isAssignableFrom(m.getReturnType())) {
         // TODO: PairBuilder.valueType
         JsonObject valueType = TYPE_STRING;
         if (PartialBuilder.class.isAssignableFrom(m.getReturnType())) {
            try {
               Class<?> innerBuilder = m.getReturnType().getMethod("withKey", String.class).getReturnType();
               valueType = new JsonObject().put("oneOf", new JsonArray().add(valueType).add(describeBuilder(innerBuilder)));
            } catch (NoSuchMethodException e) {
               throw new IllegalStateException(e);
            }
         }

         JsonObject patternProperties = new JsonObject().put(".*", valueType);
         JsonObject object = new JsonObject()
               .put("type", "object")
               .put("patternProperties", patternProperties);
         JsonObject sequenceObject = new JsonObject()
               .put("type", "object")
               .put("minProperties", 1)
               .put("maxProperties", 1)
               .put("patternProperties", patternProperties);
         options.add(object);
         options.add(arrayOf(sequenceObject));
      }
      if (BaseSequenceBuilder.class.isAssignableFrom(m.getReturnType())) {
         options.add(new JsonObject()
               .put("type", "array")
               .put("additionalItems", false)
               .put("items", new JsonObject().put("$ref", "#/definitions/step")));
      }
      if (ListBuilder.class.isAssignableFrom(m.getReturnType())) {
         options.add(new JsonObject()
               .put("type", "array")
               .put("additionalItems", false)
               .put("items", TYPE_STRING));
      }
      if (MappingListBuilder.class.isAssignableFrom(m.getReturnType())) {
         JsonObject item;
         try {
            item = describeBuilder(m.getReturnType().getMethod("addItem").getReturnType());
         } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
         }
         options.add(item);
         options.add(arrayOf(item));
      }
      if (ServiceLoadedBuilderProvider.class.isAssignableFrom(m.getReturnType())) {
         ParameterizedType type = (ParameterizedType) m.getAnnotatedReturnType().getType();
         Class<?> builderClazz = getRawClass(type.getActualTypeArguments()[0]);
         JsonObject discriminator = getServiceLoadedImplementations(builderClazz);
         options.add(discriminator);
         options.add(arrayOf(discriminator));
      }
      if (m.getReturnType().getName().endsWith("Builder")) {
         JsonObject builderReference = describeBuilder(m.getReturnType());
         options.add(builderReference);
         options.add(arrayOf(builderReference));
      }

      if (options.isEmpty()) {
         return null;
      } else if (options.size() == 1) {
         return options.get(0);
      } else {
         return new JsonObject().put("oneOf", new JsonArray(options));
      }
   }

   private JsonObject getType(Method m) {
      Class<?> type = m.getParameters()[0].getType();
      if (isIntegral(type)) {
         return new JsonObject().put("type", "integer");
      } else if (isFloat(type)) {
         return new JsonObject().put("type", "number");
      } else if (type == Boolean.class || type == boolean.class) {
         return new JsonObject().put("type", "boolean");
      } else if (type.isEnum()) {
         return new JsonObject().put("enum", makeEnum(type));
      } else {
         return TYPE_STRING;
      }
   }

   private JsonObject getServiceLoadedImplementations(Class<?> builderClazz) {
      JsonObject implementations = new JsonObject();
      JsonObject discriminator = new JsonObject()
            .put("type", "object")
            .put("additionalProperties", false)
            .put("minProperties", 1)
            .put("maxProperties", 1)
            .put("properties", implementations);
      for (Map.Entry<String, BuilderInfo<?>> entry : ServiceLoadedBuilderProvider.builders(builderClazz).entrySet()) {
         Class<?> implClazz = entry.getValue().implClazz;
         JsonObject serviceLoadedProperty = describeBuilder(implClazz);
         if (InitFromParam.class.isAssignableFrom(implClazz)) {
            serviceLoadedProperty = new JsonObject()
                  .put("oneOf", new JsonArray().add(serviceLoadedProperty).add(TYPE_STRING));
         }
         addProperty(implementations, entry.getKey(), serviceLoadedProperty);
      }
      definitions.put(builderClazz.getName(), discriminator);
      return new JsonObject().put("$ref", "#/definitions/" + builderClazz.getName());
   }

   private static void addProperty(JsonObject properties, String name, JsonObject newProperty) {
      JsonObject existingProperty = properties.getJsonObject(name);
      if (existingProperty == null) {
         properties.put(name, newProperty);
         return;
      } else if (existingProperty.equals(newProperty)) {
         return;
      }
      JsonArray oneOf = existingProperty.getJsonArray("oneOf");
      if (oneOf == null) {
         properties.put(name, new JsonObject().put("oneOf", new JsonArray().add(existingProperty).add(newProperty)));
      } else {
         oneOf.add(newProperty);
      }
   }

   private static JsonObject arrayOf(JsonObject sequenceObject) {
      return new JsonObject()
            .put("type", "array")
            .put("minLength", 1)
            .put("additionalItems", false)
            .put("items", sequenceObject);
   }

   private static JsonArray makeEnum(Class<?> type) {
      JsonArray array = new JsonArray();
      for (Object e : type.getEnumConstants()) {
         array.add(((Enum) e).name());
      }
      return array;
   }

   private static boolean isFloat(Class<?> type) {
      return type == Double.class || type == double.class || type == Float.class || type == float.class;
   }

   private static boolean isIntegral(Class<?> type) {
      return type == Integer.class || type == int.class || type == Long.class || type == long.class;
   }
}
