package io.hyperfoil.grpc.config;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.protobuf.Descriptors;

import io.apicurio.registry.utils.protobuf.schema.FileDescriptorUtils;
import io.apicurio.registry.utils.protobuf.schema.FileDescriptorUtils.ProtobufSchemaContent;
import io.hyperfoil.api.config.BenchmarkData;
import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.BuilderBase;

public class ProtoConfig implements Serializable {

   public static class GrpcProtoSchema implements Serializable {
      private final String fileName;
      private final String schemaDefinition;

      private GrpcProtoSchema(String fileName, String schemaDefinition) {
         this.fileName = Objects.requireNonNull(fileName);
         this.schemaDefinition = Objects.requireNonNull(schemaDefinition);
      }

      public static GrpcProtoSchema[] of(BenchmarkData data, String[] importProtos) {
         GrpcProtoSchema[] schemas = new GrpcProtoSchema[importProtos.length];
         for (int i = 0; i < importProtos.length; i++) {
            schemas[i] = of(data, importProtos[i]);
         }
         return schemas;
      }

      public static GrpcProtoSchema of(BenchmarkData data, String fileName) {
         return new GrpcProtoSchema(fileName, data.readFileAsString(fileName, StandardCharsets.UTF_8));
      }
   }

   private final GrpcProtoSchema mainProto;
   private final GrpcProtoSchema[] importProtos;

   public ProtoConfig(final GrpcProtoSchema mainProto, final GrpcProtoSchema[] importProtos) {
      this.mainProto = mainProto;
      this.importProtos = importProtos;
   }

   public Descriptors.FileDescriptor toProtoFileDescriptor() {
      var mainProtoDesc = ProtobufSchemaContent.of(mainProto.fileName, mainProto.schemaDefinition);
      var importProtoDescs = Stream.of(importProtos)
            .map(proto -> ProtobufSchemaContent.of(proto.fileName, proto.schemaDefinition))
            .collect(Collectors.toList());
      try {
         return FileDescriptorUtils.parseProtoFileWithDependencies(mainProtoDesc, importProtoDescs);
      } catch (Throwable t) {
         throw new IllegalStateException(t);
      }
   }

   public static class Builder implements BuilderBase<Builder> {
      private final GrpcBuilder parent;
      private String mainProto;
      private final Set<String> importPaths = new HashSet<>();

      public Builder(final GrpcBuilder parent) {
         this.parent = parent;
      }

      public Builder main(String mainProto) {
         this.mainProto = mainProto;
         return this;
      }

      public Builder addImportProto(String value) {
         importPaths.add(value);
         return this;
      }

      public ProtoConfig build(BenchmarkData data) {
         if (mainProto == null) {
            throw new BenchmarkDefinitionException("mainProto must be set!");
         }
         try {
            return new ProtoConfig(GrpcProtoSchema.of(data, mainProto),
                  GrpcProtoSchema.of(data, importPaths.toArray(new String[0])));
         } catch (Throwable t) {
            throw new BenchmarkDefinitionException("Cannot load proto files", t);
         }
      }

      public GrpcBuilder end() {
         return parent;
      }
   }
}
