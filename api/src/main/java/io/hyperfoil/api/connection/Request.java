package io.hyperfoil.api.connection;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.hyperfoil.api.config.StartTimeSource;
import io.hyperfoil.api.session.SequenceInstance;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.statistics.Statistics;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

public abstract class Request implements Callable<Void>, GenericFutureListener<Future<Void>>, StartTimeSource {
   private static final Logger log = LogManager.getLogger(Request.class);

   public final Session session;
   private final long[] timestamps = new long[2];
   private long startTimestampMillis;
   private long startTimestampNanos;
   private long firedTimestampMillis;
   private SequenceInstance sequence;
   private SequenceInstance completionSequence;
   private Statistics statistics;
   // Pending timeout, see RequestTimeouts: index of the queue and the slot in it (-1 when none) and the deadline.
   int timeoutQueue = -1;
   int timeoutSlot = -1;
   long timeoutDeadline;
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
      cancelTimeout();
      if (status != Status.COMPLETED) {
         result = Result.TIMED_OUT;
         statistics.incrementTimeouts(this, session);
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
      start(sequence, statistics, false);
   }

   public void start(SequenceInstance sequence, Statistics statistics, boolean useSessionStartTime) {
      createStartTimestamp(session, useSessionStartTime, timestamps);
      startTimestampMillis = timestamps[0];
      startTimestampNanos = timestamps[1];
      firedTimestampMillis = 0;
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
      cancelTimeout();
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
      statistics.recordResponse(this, endTimestampNanos - startTimestampNanos, session);
   }

   public long startTimestampMillis() {
      return startTimestampMillis;
   }

   public long startTimestampNanos() {
      return startTimestampNanos;
   }

   public long firedTimestampMillis() {
      return this.firedTimestampMillis;
   }

   @Override
   public void setFiredTimestampMillis(Session session) {
      this.firedTimestampMillis = System.currentTimeMillis();
   }

   public void setTimeout(long timeout, TimeUnit timeUnit) {
      RequestTimeouts.current().schedule(this, session.executor(), timeUnit.toNanos(timeout));
   }

   private void cancelTimeout() {
      if (timeoutQueue >= 0) {
         RequestTimeouts.current().cancel(this);
      }
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
