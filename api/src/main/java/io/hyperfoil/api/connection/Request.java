package io.hyperfoil.api.connection;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import io.hyperfoil.api.session.SequenceInstance;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.statistics.Statistics;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.ScheduledFuture;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public abstract class Request implements Callable<Void>, GenericFutureListener<Future<Void>> {
   private static final Logger log = LogManager.getLogger(Request.class);
   private static final GenericFutureListener<Future<Object>> FAILURE_LISTENER = future -> {
      if (!future.isSuccess() && !future.isCancelled()) {
         log.error("Timeout task failed", future.cause());
      }
   };

   public final Session session;
   private long startTimestampMillis;
   private long startTimestampNanos;
   private SequenceInstance sequence;
   private SequenceInstance completionSequence;
   private Statistics statistics;
   private ScheduledFuture<?> timeoutFuture;
   private Connection connection;
   private Status status = Status.IDLE;
   private Result result = Result.VALID;

   public Request(Session session) {
      this.session = session;
   }

   /**
    * This method works as timeout handler
    */
   @Override
   public Void call() {
      int uniqueId = session == null ? -1 : session.uniqueId();
      log.warn("#{} Request timeout, closing connection {}", uniqueId, connection);
      timeoutFuture = null;
      if (status != Status.COMPLETED) {
         result = Result.TIMED_OUT;
         statistics.incrementTimeouts(startTimestampMillis);
         if (connection == null) {
            log.warn("#{} connection is already null", uniqueId);
         } else {
            connection.close();
         }
         // handleThrowable sets the request completed
      } else {
         log.trace("#{} Request {} is already completed.", uniqueId, this);
      }
      return null;
   }

   public void start(SequenceInstance sequence, Statistics statistics) {
      this.startTimestampMillis = System.currentTimeMillis();
      this.startTimestampNanos = System.nanoTime();
      this.sequence = sequence;
      // The reason for using separate sequence reference just for the sake of decrementing
      // its counter is that the request sequence might be overridden (wrapped) through
      // `unsafeEnterSequence` when the request is cancelled (e.g. because the session
      // is being stopped). In that case we would decrement counters on a wrong sequence.
      this.completionSequence = sequence.incRefCnt();
      this.statistics = statistics;
      this.status = Status.RUNNING;
      this.result = Result.VALID;
   }

   public void attach(Connection connection) {
      this.connection = connection;
   }

   public Status status() {
      return status;
   }

   public boolean isValid() {
      return result == Result.VALID;
   }

   public void markInvalid() {
      result = Result.INVALID;
   }

   public void setCompleting() {
      status = Status.COMPLETING;
   }

   public boolean isRunning() {
      return status == Status.RUNNING;
   }

   public boolean isCompleted() {
      return status == Status.COMPLETED || status == Status.IDLE;
   }

   public void setCompleted() {
      if (timeoutFuture != null) {
         timeoutFuture.cancel(false);
         timeoutFuture = null;
      }
      connection = null;
      sequence = null;
      // handleEnd may indirectly call handleThrowable which calls setCompleted first
      if (status != Status.IDLE) {
         status = Status.COMPLETED;
         completionSequence.decRefCnt(session);
         completionSequence = null;
      }
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

   public void recordResponse(long endTimestampNanos) {
      statistics.recordResponse(startTimestampMillis, endTimestampNanos - startTimestampNanos);
   }

   public long startTimestampMillis() {
      return startTimestampMillis;
   }

   public long startTimestampNanos() {
      return startTimestampNanos;
   }

   public void setTimeout(long timeout, TimeUnit timeUnit) {
      timeoutFuture = session.executor().schedule(this, timeout, timeUnit);
      timeoutFuture.addListener(FAILURE_LISTENER);
   }

   @Override
   public void operationComplete(Future<Void> future) {
      // This is called when the request is written on the wire
      // It doesn't make sense to throw any exceptions from this method
      // since DefaultPromise.notifyListener0 would swallow them with a warning.
      if (!future.isSuccess()) {
         log.error("Failed to write request {} to {}", this, connection);
         if (connection != null) {
            connection.close();
         }
      }
   }

   public abstract void release();

   public void enter() {
      session.currentSequence(sequence);
      session.currentRequest(this);
   }

   public void exit() {
      session.currentSequence(null);
      session.currentRequest(null);
   }

   /*
    * Use this method very cautiously!
    */
   public void unsafeEnterSequence(SequenceInstance sequence) {
      this.sequence = sequence;
   }

   protected void setIdle() {
      status = Status.IDLE;
   }

   @Override
   public String toString() {
      return "(#" + session.uniqueId() + ", " + status + ", " + result + ")";
   }

   public enum Result {
      VALID,
      INVALID,
      TIMED_OUT,
   }

   public enum Status {
      IDLE, // in pool
      RUNNING,
      COMPLETING,
      COMPLETED,
   }
}
