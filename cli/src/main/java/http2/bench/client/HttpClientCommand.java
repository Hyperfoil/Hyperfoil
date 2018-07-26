package http2.bench.client;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import http2.bench.CommandBase;
import io.sailrocket.core.client.HttpClientProvider;
import io.sailrocket.core.client.HttpClientRunner;
import io.vertx.core.http.HttpVersion;

import java.util.Collections;
import java.util.List;

/**
 * Todo : use pool buffers wherever possible
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@Parameters()
public class HttpClientCommand extends CommandBase {

    public long currentTime;

    @Parameter(names = {"--provider"})
    public HttpClientProvider provider = HttpClientProvider.netty;

    @Parameter(names = {"-p", "--protocol"})
    public HttpVersion protocol = HttpVersion.HTTP_2;

    @Parameter(names = {"-d", "--duration"})
    public String durationParam = "30s";

    @Parameter(names = {"-c", "--connections"})
    public int connections = 1;

    @Parameter(names = {"-o", "--out"})
    public String out = null;

    @Parameter(names = {"-b", "--body"})
    public String bodyParam = null;

    @Parameter(names = {"-q", "--concurrency"}, description = "The concurrency per connection: number of pipelined requests for HTTP/1.1, max concurrent streams for HTTP/2")
    public int concurrency = 1;

    @Parameter
    public List<String> uriParam;

    @Parameter(names = {"-r", "--rate"}, variableArity = true)
    public List<Integer> rates = Collections.singletonList(100); // rate per second

    @Parameter(names = {"-w", "--warmup"})
    public String warmupParam = "0";

    @Parameter(names = {"-t", "--threads"}, description = "Number of threads to use")
    public int threads = 1;

    @Parameter(names = {"--tags"})
    public String tagString = null;

    @Override
    public void run() throws Exception {

        HttpClientRunner httpClientRunner = new HttpClientRunner(
                provider,
                protocol,
                durationParam,
                connections,
                out,
                bodyParam,
                concurrency,
                uriParam,
                rates,
                warmupParam,
                threads,
                tagString
        );

        httpClientRunner.run();
    }
}
