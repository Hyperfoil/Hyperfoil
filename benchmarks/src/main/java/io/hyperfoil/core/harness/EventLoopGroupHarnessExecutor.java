package io.hyperfoil.core.harness;

import io.netty.util.concurrent.DefaultEventExecutorGroup;
import java.util.concurrent.CopyOnWriteArraySet;

public class EventLoopGroupHarnessExecutor extends DefaultEventExecutorGroup {

    public static final CopyOnWriteArraySet<EventLoopGroupHarnessExecutor> TOTAL_EVENT_EXECUTORS = new CopyOnWriteArraySet<>();

    public EventLoopGroupHarnessExecutor(int nThreads, String ignored) {
        super(nThreads);
        TOTAL_EVENT_EXECUTORS.add(this);
    }
}
