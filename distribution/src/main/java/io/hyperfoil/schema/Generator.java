package io.hyperfoil.schema;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import io.hyperfoil.api.config.BaseSequenceBuilder;
import io.hyperfoil.api.config.InitFromParam;
import io.hyperfoil.api.config.ListBuilder;
import io.hyperfoil.api.config.MappingListBuilder;
import io.hyperfoil.api.config.PairBuilder;
import io.hyperfoil.api.config.PartialBuilder;
import io.hyperfoil.api.config.StepBuilder;
import io.hyperfoil.core.builders.BuilderInfo;
import io.hyperfoil.core.builders.ServiceLoadedBuilderProvider;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class Generator extends BaseGenerator {
   private static final JsonObject TYPE_NULL = new JsonObject().put("type", "null");
   private static final JsonObject TYPE_STRING = new JsonObject().put("type", "string");
   private static final Comparator<JsonObject> JSON_COMPARATOR = new Comparator<JsonObject>() {
      @Override
      public int compare(JsonObject o1, JsonObject o2) {
         TreeSet<String> keys = new TreeSet<>(o1.fieldNames());
         keys.addAll(o2.fieldNames());
         for (String key : keys) {
            int result = compareItems(o1.getValue(key), o2.getValue(key));
            if (result != 0) {
               return result;
            }
         }
         return 0;
      }

      private int compareItems(Object v1, Object v2) {
         // Sorting nulls at end
         if (v1 == null) {
            return v2 == null ? 0 : 1;
         } else if (v2 == null) {
            return -1;
         }
         if (v1 instanceof JsonObject) {
            if (v2 instanceof JsonObject) {
               return this.compare((JsonObject) v1, (JsonObject) v2);
            } else {
               throw new IllegalArgumentException(v1 + ", " + v2);
            }
         } else if (v1 instanceof JsonArray) {
            JsonArray a1 = (JsonArray) v1;
            JsonArray a2 = (JsonArray) v2;
            for (int i = 0; i < Math.min(a1.size(), a2.size()); ++i) {
               int result = compareItems(a1.getValue(i), a2.getValue(i));
               if (result != 0) {
                  return result;
               }
            }
            return Integer.compare(a1.size(), a2.size());
         } else if (v1 instanceof String) {
            if (v2 instanceof String) {
               return ((String) v1).compareTo((String) v2);
            } else {
               throw new IllegalArgumentException(v1 + ", " + v2);
            }
         } else if (v1 instanceof Integer) {
            if (v2 instanceof Integer) {
               return ((Integer) v1).compareTo((Integer) v2);
            } else {
               throw new IllegalArgumentException(v1 + ", " + v2);
            }
         } else if (v1 instanceof Boolean) {
            if (v2 instanceof Boolean) {
               boolean b1 = (boolean) v1;
               if (b1 != (boolean) v2) {
                  return b1 ? 1 : -1;
               }
            } else {
               throw new IllegalArgumentException(v1 + ", " + v2);
            }
         } else {
            throw new IllegalArgumentException(String.valueOf(v1));
         }
         return 0;
      }
   };

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
      JsonObject schemaDefinitions = schema.getJsonObject("definitions");
      definitions = new JsonObject(new TreeMap<>());
      JsonObject step = schemaDefinitions.getJsonObject("step");
      JsonArray oneOf = step.getJsonArray("oneOf");
      TreeMap<String, Object> sortedMap = new TreeMap<>();
      sortedMap.putAll(oneOf.getJsonObject(0).getJsonObject("properties").getMap());
      JsonObject builders = new JsonObject(sortedMap);
      oneOf.getJsonObject(0).put("properties", builders);
      JsonArray simpleBuilders = oneOf.getJsonObject(1).getJsonArray("enum");
      simpleBuilders.clear();

      for (Map.Entry<String, BuilderInfo<?>> entry : ServiceLoadedBuilderProvider.builders(StepBuilder.class).entrySet()) {
         @SuppressWarnings("unchecked")
         Class<StepBuilder<?>> implClazz = (Class<StepBuilder<?>>) entry.getValue().implClazz;
         addBuilder(builders, simpleBuilders, entry.getKey(), implClazz, InitFromParam.class.isAssignableFrom(implClazz));
      }

      if (simpleBuilders.size() == 0) {
         oneOf.remove(1);
      }
      definitions.forEach(e -> schemaDefinitions.put(e.getKey(), e.getValue()));

      Files.write(output, schema.encodePrettily().getBytes(StandardCharsets.UTF_8));
   }

   private void addBuilder(JsonObject builders, JsonArray simpleBuilders, String name, Class<?> builder, boolean inline) {
      JsonObject properties = new JsonObject(new TreeMap<>());
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
         describeBuilder(builder, definition, new JsonObject(new TreeMap<>()));
      }
      return new JsonObject().put("$ref", "#/definitions/" + builder.getName());
   }

   private void describeBuilder(Class<?> builder, JsonObject definition, JsonObject properties) {
      definition.put("type", "object");
      definition.put("additionalProperties", false);
      definition.put("properties", properties);
      if (PartialBuilder.class.isAssignableFrom(builder)) {
         try {
            Method withKey = builder.getMethod("withKey", String.class);
            Class<?> innerBuilder = withKey.getReturnType();
            JsonObject propertyType;
            if (ServiceLoadedBuilderProvider.class == innerBuilder) {
               propertyType = getServiceLoadedImplementations(
                     getRawClass(((ParameterizedType) withKey.getGenericReturnType()).getActualTypeArguments()[0]));
            } else {
               propertyType = describeBuilder(innerBuilder);
            }
            definition.put("patternProperties", new JsonObject().put(".*", propertyType));
         } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
         }
      }
      findProperties(builder, m -> {
         JsonObject property = describeMethod(m.getDeclaringClass(), m);
         if (property != null) {
            addProperty(properties, m.getName(), property);
         }
      });
   }

   private JsonObject describeMethod(Class<?> builder, Method m) {
      if (m.getReturnType().isAssignableFrom(builder)) {
         if (m.getParameterCount() == 0) {
            return TYPE_NULL;
         } else if (m.getParameterCount() == 1) {
            return getType(m);
         } else {
            throw new IllegalStateException();
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
      if (BaseSequenceBuilder.class.isAssignableFrom(m.getReturnType())) {
         options.add(new JsonObject()
               .put("type", "array")
               .put("additionalItems", false)
               .put("items", new JsonObject().put("$ref", "#/definitions/step")));
         // return early to avoid reporting BaseSequenceBuilder
         return optionsToObject(options);
      }
      if (m.getReturnType().getName().endsWith("Builder")) {
         JsonObject builderReference = describeBuilder(m.getReturnType());
         options.add(builderReference);
         options.add(arrayOf(builderReference));
      }

      return optionsToObject(options);
   }

   private JsonObject optionsToObject(ArrayList<JsonObject> options) {
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
      for (Map.Entry<String, BuilderInfo<?>> entry : new TreeMap<>(ServiceLoadedBuilderProvider.builders(builderClazz))
            .entrySet()) {
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
      ArrayList<JsonObject> props = new ArrayList<>();
      JsonArray existingOneOf = existingProperty.getJsonArray("oneOf");
      if (existingOneOf == null) {
         props.add(existingProperty);
      } else {
         existingOneOf.forEach(p -> props.add((JsonObject) p));
      }
      JsonArray newOneOf = newProperty.getJsonArray("oneOf");
      if (newOneOf == null) {
         props.add(newProperty);
      } else {
         newOneOf.forEach(p -> props.add((JsonObject) p));
      }
      props.sort(JSON_COMPARATOR);
      properties.put(name, new JsonObject().put("oneOf", new JsonArray(props)));
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
