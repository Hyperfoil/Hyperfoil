package io.hyperfoil.api.statistics;

import java.io.Serializable;
import java.util.ServiceLoader;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.jsontype.NamedType;

import io.vertx.core.json.jackson.DatabindCodec;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
public interface StatsExtension extends Serializable, Cloneable {
   static void registerSubtypes() {
      ServiceLoader.load(StatsExtension.class).stream().forEach(provider -> {
         JsonTypeName typeName = provider.type().getAnnotation(JsonTypeName.class);
         if (typeName != null) {
            NamedType namedType = new NamedType(provider.type(), typeName.value());
            DatabindCodec.mapper().registerSubtypes(namedType);
            DatabindCodec.prettyMapper().registerSubtypes(namedType);
         }
      });
   }

   @JsonIgnore
   boolean isNull();

   void add(StatsExtension other);

   void subtract(StatsExtension other);

   void reset();

   StatsExtension clone();

   String[] headers();

   String byHeader(String header);
}
