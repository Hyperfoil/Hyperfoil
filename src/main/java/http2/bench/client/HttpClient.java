package http2.bench.client;

import java.util.function.Consumer;

public interface HttpClient {

  void start(Consumer<Void> completionHandler);

  HttpRequest request(String method, String path);

  long bytesRead();

  long bytesWritten();

  void resetStatistics();

  void shutdown();
}
