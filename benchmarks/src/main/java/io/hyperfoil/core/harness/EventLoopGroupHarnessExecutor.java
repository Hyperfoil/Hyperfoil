package io.hyperfoil.core.harness;

import java.util.concurrent.CopyOnWriteArraySet;

import io.netty.util.concurrent.DefaultEventExecutorGroup;

public class EventLoopGroupHarnessExecutor extends DefaultEventExecutorGroup {

   public static final CopyOnWriteArraySet<EventLoopGroupHarnessExecutor> TOTAL_EVENT_EXECUTORS = new CopyOnWriteArraySet<>();

   public EventLoopGroupHarnessExecutor(int nThreads, String ignored) {
      super(nThreads);
      TOTAL_EVENT_EXECUTORS.add(this);
   }
}
