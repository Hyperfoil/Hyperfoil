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
import io.sailrocket.core.client.WorkerImpl;
import io.sailrocket.core.impl.SequenceFactory;
import io.sailrocket.core.impl.SequenceImpl;
import io.sailrocket.core.impl.StepImpl;
import org.junit.Assert;
import org.junit.Ignore;
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

    @Test
    @Ignore
    public void TestSequenceBroke() throws ExecutionException, InterruptedException {


        CompletableFuture<SequenceContext> sequenceFuture = null;
        Queue<String> executionOrder;

        //build pipeline using for iterator
        executionOrder = new ConcurrentLinkedQueue<>();
        sequenceFuture = buildPipeline(buildSequence(), executionOrder);

        sequenceFuture.get();
        executionOrder.add("done");

        runAssertions(executionOrder);

    }

    @Test
    @Ignore
    public void TestSequenceIterator() throws ExecutionException, InterruptedException {


        CompletableFuture<SequenceContext> sequenceFuture = null;
        Queue<String> executionOrder;

        //build pipeline using for iterator
        executionOrder = new ConcurrentLinkedQueue<>();
        sequenceFuture = buildPipelineIterator(buildSequence(), executionOrder);

        sequenceFuture.get();
        executionOrder.add("done");

        runAssertions(executionOrder);

    }

    @Test
    @Ignore
    public void TestSequenceReduce() throws ExecutionException, InterruptedException {


        CompletableFuture<SequenceContext> sequenceFuture = null;
        Queue<String> executionOrder;

        //build pipeline using for iterator
        executionOrder = new ConcurrentLinkedQueue<>();
        sequenceFuture = buildPipelineReduce(buildSequence(), executionOrder);

        sequenceFuture.get();
        executionOrder.add("done");

        runAssertions(executionOrder);

    }

    @Test
    @Ignore
    public void TestSequenceFoolish() throws ExecutionException, InterruptedException {

        CompletableFuture<SequenceContext> sequenceFuture = null;
        Queue<String> executionOrder;


        //build pipeline like a fool
        executionOrder = new ConcurrentLinkedQueue<>();
        sequenceFuture = buildPipelineLikeFool(buildSequence(), executionOrder);

        sequenceFuture.get();
        executionOrder.add("done");

        runAssertions(executionOrder);

    }

    @Test
    @Ignore
    public void TestSequenceSemiFoolish() throws ExecutionException, InterruptedException {

        CompletableFuture<SequenceContext> sequenceFuture = null;
        Queue<String> executionOrder;


        //build pipeline like a fool
        executionOrder = new ConcurrentLinkedQueue<>();
        sequenceFuture = buildPipelineSemiFoolishly(buildSequence(), executionOrder);

        sequenceFuture.get();
        executionOrder.add("done");

        runAssertions(executionOrder);

    }

    private CompletableFuture<SequenceContext> buildPipelineLikeFool(SequenceImpl sequence, Queue<String> executionOrder) {

 /*       CompletableFuture<SequenceContext> sequenceFuture = sequence.getSteps().get(0).asyncExec(new SequenceContextImpl(new DummyHttpClientPool(executionOrder, "foolish"), new WorkerImpl(new SequenceStats(), 1)));
        sequenceFuture = sequenceFuture.thenCompose(session -> sequence.getSteps().get(1).asyncExec(session));
        sequenceFuture = sequenceFuture.thenCompose(session -> sequence.getSteps().get(2).asyncExec(session));

        return sequenceFuture;
*/
        return null;
    }

    private CompletableFuture<SequenceContext> buildPipelineSemiFoolishly(SequenceImpl sequence, Queue<String> executionOrder) {

/*
        //TODO:: theres got to be a better way of doing this, this is a nasty code smell
        CompletableFuture<SequenceContext> sequenceFuture = null;
        for (int i = 0; i < sequence.getSteps().size(); i++) {
            if (i == 0) {
                sequenceFuture = sequence.getSteps().get(i).asyncExec(new SequenceContextImpl(new DummyHttpClientPool(executionOrder, "semiFoolish"), new WorkerImpl(new SequenceStats(), 1)));
            } else {
                int finalI = i;
                sequenceFuture = sequenceFuture.thenCompose(session -> sequence.getSteps().get(finalI).asyncExec(session));
            }
        }

        return sequenceFuture;
        */
        return null;
    }

    private CompletableFuture<SequenceContext> buildPipeline(SequenceImpl sequence, Queue<String> executionOrder) {

/*        CompletableFuture<SequenceContext> sequenceFuture = null;

        //There appears to be an ordering issue here, the futures are not composed correctly
        //non-determininstic behaviour building this way
        for (AsyncStep step : sequence.getSteps()) {
            if (sequenceFuture == null) {
                sequenceFuture = step.asyncExec(new SequenceContextImpl(new DummyHttpClientPool(executionOrder, "normal"), new WorkerImpl(new SequenceStats(), 1)));
            } else {
                sequenceFuture.thenCompose(session -> step.asyncExec(session));
            }
        }

        return sequenceFuture;*/
        return null;
    }

    private CompletableFuture<SequenceContext> buildPipelineIterator(SequenceImpl sequence, Queue<String> executionOrder) {

/*        CompletableFuture<SequenceContext> sequenceFuture = null;

        //There appears to be an ordering issue here, the futures are not composed correctly
        //non-determininstic behaviour building this way
        Iterator<AsyncStep> stepIterator = sequence.getSteps().iterator();
        AsyncStep step = null;
        while (stepIterator.hasNext()) {
            step = stepIterator.next();
            if (sequenceFuture == null) {
                sequenceFuture = step.asyncExec(new SequenceContextImpl(new DummyHttpClientPool(executionOrder, "normal"), new WorkerImpl(1)));
            } else {
                AsyncStep finalStep = step;
                sequenceFuture.thenCompose(session -> finalStep.asyncExec(session));
            }
        }

        return sequenceFuture;*/
        return null;
    }


    private CompletableFuture<SequenceContext> buildPipelineReduce(SequenceImpl sequence, Queue<String> executionOrder) {

/*
        CompletableFuture<SequenceContext> startFuture = new CompletableFuture().supplyAsync(() -> new SequenceContextImpl(new DummyHttpClientPool(executionOrder, "normal"), new WorkerImpl(new SequenceStats(), 1)));
        return sequence.getSteps().stream()
                .<CompletableFuture<SequenceContext>>reduce(startFuture, (sequenceFuture, step) -> addStep(sequenceFuture, step), (sequenceFuture, e) -> passSequence(sequenceFuture));
*/

        return null;

    }

    private CompletableFuture<SequenceContext> addStep(CompletableFuture<SequenceContext> future, AsyncStep step) {
        return future.thenCompose(sequenceState -> step.asyncExec(sequenceState));
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
