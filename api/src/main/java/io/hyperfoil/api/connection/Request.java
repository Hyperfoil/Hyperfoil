package io.hyperfoil.api.connection;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.hyperfoil.api.session.SequenceInstance;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.statistics.Statistics;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.ScheduledFuture;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public abstract class Request implements Callable<Void>, GenericFutureListener<Future<Void>> {
   private static final TimeoutException TIMEOUT_EXCEPTION = new TimeoutException();
   private static final Logger log = LoggerFactory.getLogger(Request.class);

   public final Session session;
   private long startTimestampMillis;
   private long startTimestampNanos;
   private long sendTimestampNanos;
   private SequenceInstance sequence;
   private Statistics statistics;
   private ScheduledFuture<?> timeoutFuture;
   private Connection connection;
   private boolean completed = true;
   private boolean valid = true;

   public Request(Session session) {
      this.session = session;
   }

   /**
    * This method works as timeout handler
    */
   @Override
   public Void call() {
      int uniqueId = session == null ? -1 : session.uniqueId();
      log.error("#{} Firing timeout on connection {}", uniqueId, connection);
      timeoutFuture = null;
      if (!isCompleted()) {
         statistics.incrementTimeouts(startTimestampNanos);
         handleThrowable(TIMEOUT_EXCEPTION);
         // handleThrowable sets the request completed
      } else {
         log.error("#{} Request {} is already completed.", uniqueId, this);
      }
      return null;
   }

   protected abstract void handleThrowable(Throwable throwable);

   public void start(SequenceInstance sequence, Statistics statistics) {
      this.startTimestampMillis = System.currentTimeMillis();
      this.startTimestampNanos = System.nanoTime();
      this.sequence = sequence;
      this.statistics = statistics;
      this.completed = false;
   }

   public void attach(Connection connection) {
      this.connection = connection;
   }

   public boolean isValid() {
      return valid;
   }

   public void markInvalid() {
      valid = false;
   }

   public boolean isCompleted() {
      return completed;
   }

   public void setCompleted() {
      if (timeoutFuture != null) {
         timeoutFuture.cancel(false);
         timeoutFuture = null;
      }
      connection = null;
      completed = true;
      valid = true;
   }

   public Connection connection() {
      return connection;
   }

   public SequenceInstance sequence() {
      return sequence;
   }

   public Statistics statistics() {
      return statistics;
   }

   public long startTimestampMillis() {
      return startTimestampMillis;
   }

   public long startTimestampNanos() {
      return startTimestampNanos;
   }

   public long sendTimestampNanos() {
      return sendTimestampNanos;
   }

   public void setTimeout(long timeout, TimeUnit timeUnit) {
      timeoutFuture = session.executor().schedule(this, timeout, timeUnit);
   }

   @Override
   public void operationComplete(Future<Void> future) {
      // This is called when the request is written on the wire
      sendTimestampNanos = System.nanoTime();
   }

   /**
    * Processors for any type of request.
    */
   public interface ProcessorBuilder extends Processor.Builder<Request> {}
}
