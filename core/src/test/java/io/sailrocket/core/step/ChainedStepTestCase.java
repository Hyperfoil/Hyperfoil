package io.sailrocket.core.step;

import io.sailrocket.core.AsyncEnv;
import io.sailrocket.core.client.HttpClientPoolFactory;
import io.sailrocket.core.client.HttpClientProvider;
import io.vertx.core.http.HttpVersion;
import org.junit.Ignore;
import org.junit.Test;

public class ChainedStepTestCase extends AsyncEnv {

    HttpClientPoolFactory clientBuilder;

    public ChainedStepTestCase() {
        super();
        clientBuilder = HttpClientProvider.vertx.builder()
                .threads(ASYNC_THREADS)
                .ssl(false)
                .port(8080)
                .host("localhost")
                .size(4)
                .concurrency(2)
                .protocol(HttpVersion.HTTP_1_1);
    }


    @Test
    @Ignore
    public void runChainedTest() {
//        try {
//
//            HttpClientPool httpClientPool = clientBuilder.build();
//
//            run(new RequestContext(new SequenceContextImpl(null, workers.get(0)));
//
//            runLatch.await(2, TimeUnit.MINUTES);
//
//        } catch (Exception e) {
//            Assert.fail(e.getMessage());
//        }

    }
}
