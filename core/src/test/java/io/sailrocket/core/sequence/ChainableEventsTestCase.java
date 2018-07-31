package io.sailrocket.core.sequence;

import io.sailrocket.api.HttpClient;
import io.sailrocket.api.HttpMethod;
import io.sailrocket.api.HttpRequest;
import io.sailrocket.api.SequenceState;
import io.sailrocket.api.Step;
import io.sailrocket.core.impl.ClientSessionImpl;
import io.sailrocket.core.impl.SequenceFactory;
import io.sailrocket.core.impl.SequenceImpl;
import io.sailrocket.core.impl.StepImpl;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;


@RunWith(Parameterized.class)
public class ChainableEventsTestCase {

    @Parameterized.Parameters
    public static Object[][] data() {
        return new Object[100][0];
    }

    @Test
    public void TestSequence() throws ExecutionException, InterruptedException {
        Queue<String> executionOrder;

        //build pipeline using for iterator
        executionOrder = new ConcurrentLinkedQueue<>();

        SequenceImpl sequence = buildSequence();
        sequence.setHttpClient(new DummyHttpClient(executionOrder, "TestSequence"));
        CompletableFuture<SequenceState> sequenceFuture =  SequenceFactory.buildSequanceFuture(sequence);

        sequenceFuture.get();
        executionOrder.add("done");

        runAssertions(executionOrder);

    }

    @Test
    @Ignore
    public void TestSequenceBroke() throws ExecutionException, InterruptedException {


        CompletableFuture<SequenceState> sequenceFuture = null;
        Queue<String> executionOrder;

        //build pipeline using for iterator
        executionOrder = new ConcurrentLinkedQueue<>();
        sequenceFuture = buildPipeline(buildSequence(), executionOrder);

        sequenceFuture.get();
        executionOrder.add("done");

        runAssertions(executionOrder);

    }

    @Test
    public void TestSequenceFoolish() throws ExecutionException, InterruptedException {

        CompletableFuture<SequenceState> sequenceFuture = null;
        Queue<String> executionOrder;


        //build pipeline like a fool
        executionOrder = new ConcurrentLinkedQueue<>();
        sequenceFuture = buildPipelineLikeFool(buildSequence(), executionOrder);

        sequenceFuture.get();
        executionOrder.add("done");

        runAssertions(executionOrder);

    }

    @Test
    public void TestSequenceSemiFoolish() throws ExecutionException, InterruptedException {

        CompletableFuture<SequenceState> sequenceFuture = null;
        Queue<String> executionOrder;


        //build pipeline like a fool
        executionOrder = new ConcurrentLinkedQueue<>();
        sequenceFuture = buildPipelineSemiFoolishly(buildSequence(), executionOrder);

        sequenceFuture.get();
        executionOrder.add("done");

        runAssertions(executionOrder);

    }

    private CompletableFuture<SequenceState> buildPipelineLikeFool(SequenceImpl sequence, Queue<String> executionOrder) {

        CompletableFuture<SequenceState> sequenceFuture = sequence.getSteps().get(0).asyncExec(new ClientSessionImpl(new DummyHttpClient(executionOrder, "foolish")));
        sequenceFuture = sequenceFuture.thenCompose(session -> sequence.getSteps().get(1).asyncExec(session));
        sequenceFuture = sequenceFuture.thenCompose(session -> sequence.getSteps().get(2).asyncExec(session));

        return sequenceFuture;
    }

    private CompletableFuture<SequenceState> buildPipelineSemiFoolishly(SequenceImpl sequence, Queue<String> executionOrder) {

        //TODO:: theres got to be a better way of doing this, this is a nasty code smell
        CompletableFuture<SequenceState> sequenceFuture = null;
        for (int i = 0; i < sequence.getSteps().size(); i++) {
            if (i == 0) {
                sequenceFuture = sequence.getSteps().get(i).asyncExec(new ClientSessionImpl(new DummyHttpClient(executionOrder, "semiFoolish")));
            } else {
                int finalI = i;
                sequenceFuture = sequenceFuture.thenCompose(session -> sequence.getSteps().get(finalI).asyncExec(session));
            }
        }

        return sequenceFuture;
    }

    private CompletableFuture<SequenceState> buildPipeline(SequenceImpl sequence, Queue<String> executionOrder) {

        CompletableFuture<SequenceState> sequenceFuture = null;

        //There appears to be an ordering issue here, the futures are not composed correctly
        //non-determininstic behaviour building this way
        for (Step step : sequence.getSteps()) {
            if (sequenceFuture == null) {
                sequenceFuture = step.asyncExec(new ClientSessionImpl(new DummyHttpClient(executionOrder, "normal")));
            } else {
                sequenceFuture.thenCompose(session -> step.asyncExec(session));
            }
        }

        return sequenceFuture;
    }


    private SequenceImpl buildSequence() {
        return (SequenceImpl) SequenceFactory.buildSequence(buildSteps());
    }

    private List<Step> buildSteps() {
        List<Step> steps = new ArrayList<>();

        steps.add(buildStep("/login"));
        steps.add(buildStep("/view"));
        steps.add(buildStep("/logout"));
        return steps;
    }


    private void runAssertions(Queue<String> executionOrder) {
        Assert.assertEquals(4, executionOrder.size());
        Assert.assertEquals("/login", executionOrder.remove());
        Assert.assertEquals("/view", executionOrder.remove());
        Assert.assertEquals("/logout", executionOrder.remove());
        Assert.assertEquals("done", executionOrder.remove());
    }


    private Step buildStep(String path) {
        return new StepImpl().endpoint(path);
    }


    class DummyHttpClient implements HttpClient {

        Queue<String> executionOrder;
        String pipelineType;

        public DummyHttpClient(Queue<String> executionOrder, String pipelineType) {
            this.executionOrder = executionOrder;
            this.pipelineType = pipelineType;
        }

        @Override
        public void start(Consumer<Void> completionHandler) {

        }

        @Override
        public HttpRequest request(HttpMethod method, String path) {
            executionOrder.add(path);
//            System.out.println(this.pipelineType + " - Preparing request: " + path);
            return null;
        }

        @Override
        public long inflight() {
            return 0;
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
