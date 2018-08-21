package io.sailrocket.core.sequence;

import io.netty.buffer.ByteBuf;
import io.sailrocket.api.HttpClientPool;
import io.sailrocket.api.HttpMethod;
import io.sailrocket.api.HttpRequest;
import io.sailrocket.api.Step;
import io.sailrocket.core.api.AsyncStep;
import io.sailrocket.core.api.SequenceContext;
import io.sailrocket.core.api.Worker;
import io.sailrocket.core.client.DummyHttpRequest;
import io.sailrocket.core.impl.WorkerImpl;
import io.sailrocket.core.impl.SequenceFactory;
import io.sailrocket.core.impl.SequenceImpl;
import io.sailrocket.core.impl.StepImpl;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;


//@RunWith(Parameterized.class)
public class ChainableEventsTestCase {

   public static Object[][] data() {
        return new Object[100][0];
    }

    @Test
    public void TestSequence() throws ExecutionException, InterruptedException {
        Queue<String> executionOrder;

        //build pipeline using for iterator
        executionOrder = new ConcurrentLinkedQueue<>();

        SequenceImpl sequence = buildSequence();
        HttpClientPool clientPool = new DummyHttpClientPool(executionOrder, "TestSequence");
        Worker worker = new WorkerImpl(1, clientPool);

        CompletableFuture<SequenceContext> sequenceFuture = SequenceFactory.buildSequenceFuture(sequence, worker);

        sequenceFuture.get();
        executionOrder.add("done");

        runAssertions(executionOrder);

    }

    private CompletableFuture<SequenceContext> addStep(CompletableFuture<SequenceContext> future, AsyncStep step) {
        return future.thenCompose(sequenceContext -> step.asyncExec(sequenceContext));
    }

    private CompletableFuture<SequenceContext> passSequence(CompletableFuture<SequenceContext> sequence) {
        return sequence;
    }


    private SequenceImpl buildSequence() {
        return (SequenceImpl) SequenceFactory.buildSequence(buildSteps());
    }

    private List<Step> buildSteps() {
        return Arrays.asList(buildStep("/login"), buildStep("/view"), buildStep("/logout"));
    }


    private void runAssertions(Queue<String> executionOrder) {
        Assert.assertEquals(4, executionOrder.size());
        Assert.assertEquals("/login", executionOrder.remove());
        Assert.assertEquals("/view", executionOrder.remove());
        Assert.assertEquals("/logout", executionOrder.remove());
        Assert.assertEquals("done", executionOrder.remove());
    }


    private Step buildStep(String path) {
        return new StepImpl().path(path);
    }


    class DummyHttpClientPool implements HttpClientPool {

        Queue<String> executionOrder;
        String pipelineType;

        public DummyHttpClientPool(Queue<String> executionOrder, String pipelineType) {
            this.executionOrder = executionOrder;
            this.pipelineType = pipelineType;
        }

        @Override
        public void start(Consumer<Void> completionHandler) {

        }

        @Override
        public HttpRequest request(HttpMethod method, String path, ByteBuf body) {
            executionOrder.add(path);
            return new DummyHttpRequest(method, path, new AtomicInteger(1), null);
        }

       @Override
        public long bytesRead() {
            return 0;
        }

        @Override
        public long bytesWritten() {
            return 0;
        }

        @Override
        public void resetStatistics() {
            //no-op
        }

        @Override
        public void shutdown() {
            //no-op
        }
    }
}
