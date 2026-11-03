package io.hyperfoil.clustering;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.io.NotSerializableException;
import java.io.UncheckedIOException;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import io.vertx.core.Future;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;

/**
 * {@link EventBusSend} exists for one reason: a body that cannot be serialized makes
 * {@link io.hyperfoil.clustering.messages.ObjectCodec#encodeToWire} throw on the caller's own stack, where
 * Vert.x never sees it and never fails its write promise. These tests stand in for that by making the bus
 * itself throw, and pin down that the failure is always handed to someone instead of escaping.
 */
public class EventBusSendTest {
   private static final String ADDRESS = "test.address";
   private static final Object BODY = "body";

   private final EventBus eb = mock(EventBus.class);

   /** The failure ObjectCodec raises for an unserializable body. */
   private static UncheckedIOException encodeFailure() {
      return new UncheckedIOException("Cannot serialize", new NotSerializableException("java.lang.Object"));
   }

   @Test
   public void requestShouldReportAnEncodingFailureAsAFailedFuture() {
      UncheckedIOException failure = encodeFailure();
      doThrow(failure).when(eb).request(ADDRESS, BODY);

      Future<Message<Object>> result = EventBusSend.request(eb, ADDRESS, BODY);

      assertTrue(result.failed(), "an encoding failure must arrive as a failed future, not as a throw");
      assertSame(failure, result.cause());
   }

   @Test
   public void requestShouldPassThroughTheBusResultWhenEncodingSucceeds() {
      Future<Message<Object>> reply = Future.succeededFuture();
      doReturn(reply).when(eb).request(ADDRESS, BODY);

      Future<Message<Object>> result = EventBusSend.request(eb, ADDRESS, BODY);

      assertSame(reply, result, "the helper must not interfere with a successful send");
   }

   @Test
   public void requestShouldForwardDeliveryOptionsAndStillCatchAnEncodingFailure() {
      UncheckedIOException failure = encodeFailure();
      DeliveryOptions options = new DeliveryOptions().setSendTimeout(1234);
      doThrow(failure).when(eb).request(ADDRESS, BODY, options);

      Future<Message<Object>> result = EventBusSend.request(eb, ADDRESS, BODY, options);

      assertTrue(result.failed());
      assertSame(failure, result.cause());
      verify(eb).request(ADDRESS, BODY, options);
   }

   @Test
   public void requestShouldNotifyTheHandlerAndStillFailTheFuture() {
      AtomicReference<Throwable> reported = new AtomicReference<>();
      UncheckedIOException failure = encodeFailure();
      doThrow(failure).when(eb).request(ADDRESS, BODY);

      Future<Message<Object>> result = EventBusSend.request(eb, ADDRESS, BODY, reported::set);

      assertSame(failure, reported.get(), "the caller must be told which message was lost");
      assertTrue(result.failed());
      assertSame(failure, result.cause());
   }

   @Test
   public void requestShouldLeaveTheHandlerAloneWhenEncodingSucceeds() {
      AtomicReference<Throwable> reported = new AtomicReference<>();
      Future<Message<Object>> reply = Future.succeededFuture();
      doReturn(reply).when(eb).request(ADDRESS, BODY);

      Future<Message<Object>> result = EventBusSend.request(eb, ADDRESS, BODY, reported::set);

      assertSame(reply, result);
      assertNull(reported.get(), "a healthy send must not look like a lost message");
   }

   @Test
   public void requestShouldNotTreatAFailedReplyAsAnEncodingFailure() {
      AtomicReference<Throwable> reported = new AtomicReference<>();
      Future<Message<Object>> reply = Future.failedFuture(new RuntimeException("reply timed out"));
      doReturn(reply).when(eb).request(ADDRESS, BODY);

      Future<Message<Object>> result = EventBusSend.request(eb, ADDRESS, BODY, reported::set);

      assertSame(reply, result);
      assertNull(reported.get(), "only the synchronous encoding failure belongs to the handler");
   }

   @Test
   public void sendShouldReportAnEncodingFailureToTheHandler() {
      AtomicReference<Throwable> reported = new AtomicReference<>();
      UncheckedIOException failure = encodeFailure();
      doThrow(failure).when(eb).send(ADDRESS, BODY);

      EventBusSend.send(eb, ADDRESS, BODY, reported::set);

      assertSame(failure, reported.get());
   }

   @Test
   public void sendShouldReachTheBusAndLeaveTheHandlerAloneWhenEncodingSucceeds() {
      AtomicReference<Throwable> reported = new AtomicReference<>();
      EventBusSend.send(eb, ADDRESS, BODY, reported::set);

      verify(eb).send(ADDRESS, BODY);
      verifyNoMoreInteractions(eb);
      assertNull(reported.get());
   }

   @Test
   public void publishShouldReportAnEncodingFailureToTheHandler() {
      AtomicReference<Throwable> reported = new AtomicReference<>();
      UncheckedIOException failure = encodeFailure();
      doThrow(failure).when(eb).publish(ADDRESS, BODY);

      EventBusSend.publish(eb, ADDRESS, BODY, reported::set);

      assertSame(failure, reported.get());
   }

   @Test
   public void publishShouldReachTheBusAndLeaveTheHandlerAloneWhenEncodingSucceeds() {
      AtomicReference<Throwable> reported = new AtomicReference<>();
      EventBusSend.publish(eb, ADDRESS, BODY, reported::set);

      verify(eb).publish(ADDRESS, BODY);
      verifyNoMoreInteractions(eb);
      assertNull(reported.get());
   }

   @Test
   public void requestShouldLetAnErrorEscape() {
      OutOfMemoryError oom = new OutOfMemoryError("heap");
      doThrow(oom).when(eb).request(ADDRESS, BODY);

      assertSame(oom, assertThrows(OutOfMemoryError.class, () -> EventBusSend.request(eb, ADDRESS, BODY)));
   }

   @Test
   public void sendShouldLetAnErrorEscapeRatherThanCallTheHandler() {
      AtomicReference<Throwable> reported = new AtomicReference<>();
      doThrow(new StackOverflowError()).when(eb).send(ADDRESS, BODY);

      assertThrows(StackOverflowError.class, () -> EventBusSend.send(eb, ADDRESS, BODY, reported::set));
      assertNull(reported.get(), "an Error is not a lost message");
   }
}
