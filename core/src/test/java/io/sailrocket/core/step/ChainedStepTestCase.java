package io.sailrocket.core.step;

import io.sailrocket.core.AsyncEnv;
import io.sailrocket.core.client.HttpClientPoolFactory;
import io.sailrocket.core.client.HttpClientProvider;
import io.vertx.core.http.HttpVersion;

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

}
