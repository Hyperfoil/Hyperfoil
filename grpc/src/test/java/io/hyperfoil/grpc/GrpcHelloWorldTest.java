package io.hyperfoil.grpc;

import static io.apicurio.registry.utils.protobuf.schema.FileDescriptorUtils.parseProtoFileWithDependencies;
import static io.hyperfoil.grpc.util.GrpcProtoUtils.encodeWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Message;

import io.hyperfoil.api.config.Benchmark;
import io.hyperfoil.api.statistics.StatisticsSnapshot;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.grpc.common.ServiceName;
import io.vertx.grpc.server.GrpcServerRequest;

public class GrpcHelloWorldTest extends BaseGrpcTest {
   @Test
   public void testGrpcHelloWorld() throws Exception {
      Descriptors.FileDescriptor helloWorldProto = parseProtoFileWithDependencies(TestResources.ProtoFiles.HelloWorld.file(),
            Set.of());
      // create a service name for this proto file
      Descriptors.Descriptor replyMsgDesc = helloWorldProto.findMessageTypeByName("HelloReply");
      Buffer replyBuffer = Buffer.buffer(encodeWith(replyMsgDesc, "{ \"message\": \"Hello World\"}"));
      Descriptors.MethodDescriptor methodByName = helloWorldProto.findServiceByName("Greeter").findMethodByName("SayHello");
      ServiceName greeterService = ServiceName.create(helloWorldProto.getPackage(), methodByName.getService().getName());
      final BlockingQueue<Buffer> requestsReceived = new LinkedTransferQueue<>();
      final HttpServer server = startGrpcServiceWith(greeterService, "SayHello",
            (request, protoHelloBuffer) -> {
               validateResponse(request, protoHelloBuffer, requestsReceived, replyBuffer);
            });
      blockingListen(server, 8080, "localhost");
      try {
         Benchmark benchmark = loadScenario("scenarios/GrpcHelloWorldTest.hf.yaml");
         Map<String, StatisticsSnapshot> stats = runScenario(benchmark);
         // assertEquals(requestsReceived.size(), stats.get("example").requestCount);
         // assertEquals(0, stats.get("example").connectionErrors);
         Descriptors.Descriptor requestMsgDesc = helloWorldProto.findMessageTypeByName("HelloRequest");
         Buffer expectedRequest = Buffer.buffer(encodeWith(requestMsgDesc, "{ \"name\": \"World\" }"));
         for(;;) {
            Buffer request = requestsReceived.poll();
            if (request == null) {
               break;
            }
            assertEquals(expectedRequest, request);
         }
      } finally {
         server.close();
      }
   }

   private static void validateResponse(GrpcServerRequest<Buffer, Buffer> request, Buffer protoHelloBuffer,
         BlockingQueue<Buffer> requestsReceived, Buffer replyBuffer) {
      assertTrue(request.headers().contains("x-grpc-test-echo-initial", "test-initial-metadata", false));
      var offeringAsString = request.headers().get("x-grpc-offering");
      Assertions.assertThat(offeringAsString).isNotNull();
      Assertions.assertThat(offeringAsString).matches("^[1-9][0-9]?$|^100$");
      requestsReceived.add(protoHelloBuffer);
      request.response().end(replyBuffer);
   }

   public static Message decodeWith(Descriptors.Descriptor messageDescriptor, Buffer encodedData) throws Exception {
      DynamicMessage.Builder builder = DynamicMessage.newBuilder(messageDescriptor);
      var nettyBuffer = encodedData.getByteBuf();
      builder.mergeFrom(nettyBuffer.array(), nettyBuffer.arrayOffset() + nettyBuffer.readerIndex(),
            nettyBuffer.readableBytes());
      return builder.build();
   }

}
