package io.hyperfoil.core.harness;

import io.netty.util.concurrent.DefaultThreadFactory;

import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class FastThreadLocalThreadHarnessExecutor extends ThreadPoolExecutor {

    public FastThreadLocalThreadHarnessExecutor(int maxThreads, String prefix) {
        super(maxThreads, maxThreads, 0, TimeUnit.MILLISECONDS,
                new LinkedTransferQueue<>(), new DefaultThreadFactory(prefix));
    }
}