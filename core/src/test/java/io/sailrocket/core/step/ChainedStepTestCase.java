package io.sailrocket.core.step;

import io.sailrocket.api.HttpClientPool;
import io.sailrocket.core.AsyncEnv;
import io.sailrocket.core.client.HttpClientPoolFactory;
import io.sailrocket.core.client.HttpClientProvider;
import io.sailrocket.core.client.RequestContext;
import io.sailrocket.core.impl.ClientSessionImpl;
import io.vertx.core.http.HttpVersion;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

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
    public void runChainedTest() {
        try {

            HttpClientPool httpClientPool = clientBuilder.build();

            run(new RequestContext(new ClientSessionImpl(httpClientPool, null), "/"));

            runLatch.await(2, TimeUnit.MINUTES);

        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }

    }
}
