package io.sailrocket.api;

import java.util.function.Consumer;

public interface HttpClient {

  void start(Consumer<Void> completionHandler);

  HttpRequest request(HttpMethod method, String path);

  long inflight();

  long bytesRead();

  long bytesWritten();

  void resetStatistics();

  void shutdown();
}
