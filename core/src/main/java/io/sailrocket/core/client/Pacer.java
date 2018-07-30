package io.sailrocket.core.client;

// Copied from https://github.com/LatencyUtils/LatencyUtils/blob/master/src/examples/java/LatencyLoggingDemo.java
// and adapted for our case
public class Pacer {
  private long initialStartTime;
  private double throughputInUnitsPerNsec;
  private long unitsCompleted;

  private boolean caughtUp = true;
  private long catchUpStartTime;
  private long unitsCompletedAtCatchUpStart;
  private double catchUpThroughputInUnitsPerNsec;
  private double catchUpRateMultiple;

  public Pacer(double unitsPerSec) {
    this(unitsPerSec, 3.0); // Default to catching up at 3x the set throughput
  }

  public Pacer(double unitsPerSec, double catchUpRateMultiple) {
    setThroughout(unitsPerSec);
    setCatchupRateMultiple(catchUpRateMultiple);
    initialStartTime = System.nanoTime();
  }

  public void setInitialStartTime(long initialStartTime) {
    this.initialStartTime = initialStartTime;
  }

  private void setThroughout(double unitsPerSec) {
    throughputInUnitsPerNsec = unitsPerSec / 1000000000.0;
    catchUpThroughputInUnitsPerNsec = catchUpRateMultiple * throughputInUnitsPerNsec;
  }

  private void setCatchupRateMultiple(double multiple) {
    catchUpRateMultiple = multiple;
    catchUpThroughputInUnitsPerNsec = catchUpRateMultiple * throughputInUnitsPerNsec;
  }

  /**
   * @return the time for the Step operation
   */
  long expectedNextOperationNanoTime() {
    return initialStartTime + (long) (unitsCompleted / throughputInUnitsPerNsec);
  }

  private long nsecToNextOperation() {

    long now = System.nanoTime();
    long nextStartTime = expectedNextOperationNanoTime();

    boolean sendNow = true;

    if (nextStartTime > now) {
      // We are on pace. Indicate caught_up and don't send now.}
      caughtUp = true;
      sendNow = false;
    } else {
      // We are behind
      if (caughtUp) {
        // This is the first fall-behind since we were last caught up
        caughtUp = false;
        catchUpStartTime = now;
        unitsCompletedAtCatchUpStart = unitsCompleted;
      }

      // Figure out if it's time to send, per catch up throughput:
      long unitsCompletedSinceCatchUpStart =
          unitsCompleted - unitsCompletedAtCatchUpStart;

      nextStartTime = catchUpStartTime +
          (long) (unitsCompletedSinceCatchUpStart / catchUpThroughputInUnitsPerNsec);

      if (nextStartTime > now) {
        // Not yet time to send, even at catch-up throughout:
        sendNow = false;
      }
    }

    return sendNow ? 0 : (nextStartTime - now);
  }

  /**
   * Will wait for Step operation time. After this the expectedNextOperationNanoTime() will move forward.
   *
   * @param unitCount the number of unit
   */
  public void acquire(long unitCount) {
    long nsecToNextOperation = nsecToNextOperation();
    if (nsecToNextOperation > 0) {
      sleepNs(nsecToNextOperation);
    }
    unitsCompleted += unitCount;
  }

  static void sleepNs(long ns) {
    long now = System.nanoTime();
    long deadline = now + ns;
    while (System.nanoTime() < deadline) {
      Thread.yield();
    }
  }}