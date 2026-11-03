package io.hyperfoil.clustering;

import java.util.function.Consumer;

import io.vertx.core.Future;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;

/**
 * Wraps Vert.x EventBus calls to make sure we never silently lose messages.
 * <p>
 * When Vert.x sends a message, it serializes the object. If serialization fails,
 * {@link io.hyperfoil.clustering.messages.ObjectCodec#encodeToWire} throws an exception.
 * Usually, this happens immediately on the same thread. If we do not catch this exception,
 * the caller will wait forever for a reply that will never come.
 * <p>
 * In a benchmarking tool, losing a message (like statistics) makes the final results invalid.
 * Therefore, every send must pass through this class. If a message fails to encode,
 * we catch the error and abort the benchmark immediately so we do not report false results.
 * <p>
 * Note: This class only catches errors that happen immediately (synchronous errors).
 * Once Vert.x successfully accepts the message into its queue, standard Vert.x error
 * handling takes over.
 */
final class EventBusSend {
   private EventBusSend() {
   }

   static <T> Future<Message<T>> request(EventBus eb, String address, Object message) {
      try {
         return eb.request(address, message);
      } catch (RuntimeException e) {
         return Future.failedFuture(e);
      }
   }

   static <T> Future<Message<T>> request(EventBus eb, String address, Object message, DeliveryOptions options) {
      try {
         return eb.request(address, message, options);
      } catch (RuntimeException e) {
         return Future.failedFuture(e);
      }
   }

   static <T> Future<Message<T>> request(EventBus eb, String address, Object message, Consumer<Throwable> onFailure) {
      try {
         return eb.request(address, message);
      } catch (RuntimeException e) {
         onFailure.accept(e);
         return Future.failedFuture(e);
      }
   }

   static void send(EventBus eb, String address, Object message, Consumer<Throwable> onFailure) {
      try {
         eb.send(address, message);
      } catch (RuntimeException e) {
         onFailure.accept(e);
      }
   }

   static void publish(EventBus eb, String address, Object message, Consumer<Throwable> onFailure) {
      try {
         eb.publish(address, message);
      } catch (RuntimeException e) {
         onFailure.accept(e);
      }
   }
}
