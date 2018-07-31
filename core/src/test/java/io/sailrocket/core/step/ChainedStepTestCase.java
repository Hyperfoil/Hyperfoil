package io.sailrocket.core.step;

import io.sailrocket.core.AsyncEnv;
import io.sailrocket.core.client.HttpClientBuilder;
import io.sailrocket.core.client.HttpClientProvider;
import io.sailrocket.core.client.RequestContext;
import io.vertx.core.http.HttpVersion;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

public class ChainedStepTestCase extends AsyncEnv {

    HttpClientBuilder clientBuilder;

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
    public void runChainedTest() {
        try {
            run(new RequestContext(clientBuilder, "/"));

            runLatch.await(2, TimeUnit.MINUTES);

        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }

    }
}
