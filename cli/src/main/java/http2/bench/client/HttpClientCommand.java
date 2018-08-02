package http2.bench.client;

import io.sailrocket.core.client.HttpClientProvider;
import io.sailrocket.core.client.HttpClientRunner;
import io.vertx.core.http.HttpVersion;
import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.converter.Converter;
import org.aesh.command.converter.ConverterInvocation;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Arguments;
import org.aesh.command.option.Option;
import org.aesh.command.validator.OptionValidatorException;

import java.util.Collections;
import java.util.List;

/**
 * Todo : use pool buffers wherever possible
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@CommandDefinition(name = "http-clientPool", description = "")
public class HttpClientCommand implements Command {

    @Option(name = "provider", converter = HttpClientProviderConverter.class, defaultValue = "netty")
    public HttpClientProvider provider;

    @Option(name = "protocol", shortName = 'p', converter = HttpVersionConverter.class, defaultValue = "HTTP_2")
    public HttpVersion protocol;

    @Option(name = "duration", shortName = 'd', defaultValue = "30s")
    public String durationParam;

    @Option(name = "connection", shortName = 'c', defaultValue = "1")
    public int connections;

    @Option(name = "out", shortName = 'o')
    public String out;

    @Option(name = "body", shortName = 'b')
    public String bodyParam;

    @Option(name = "concurrency", shortName = 'q', defaultValue = "1",
            description = "The concurrency per connection: number of pipelined requests for HTTP/1.1, max concurrent streams for HTTP/2")
    public int concurrency;

    @Arguments
    public List<String> uriParam;

    @Option(name = "rate", shortName = 'r', defaultValue = "100")
    public int rates; // rate per second

    @Option(name = "warmup", shortName = 'w', defaultValue = "0")
    public String warmupParam;

    @Option(name = "threads", shortName = 't', defaultValue = "1", description = "Number of threads to use")
    public int threads;

    @Option(name = "tags")
    public String tagString;

    @Override
    public CommandResult execute(CommandInvocation commandInvocation) {

        HttpClientRunner httpClientRunner = new HttpClientRunner(
                provider,
                protocol,
                durationParam,
                connections,
                out,
                bodyParam,
                concurrency,
                uriParam,
                Collections.singletonList(rates),
                warmupParam,
                threads,
                tagString
        );

        try {
            httpClientRunner.run();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return CommandResult.SUCCESS;
    }

    public static class HttpClientProviderConverter implements Converter<HttpClientProvider, ConverterInvocation> {
        @Override
        public HttpClientProvider convert(ConverterInvocation converterInvocation) throws OptionValidatorException {
            if(converterInvocation.getInput() == null)
                return HttpClientProvider.netty;
            for(HttpClientProvider provider : HttpClientProvider.values()) {
                if(provider.name().equals(converterInvocation.getInput()))
                    return provider;
            }
            //just return netty
            return HttpClientProvider.netty;
        }
    }

    public static class HttpVersionConverter implements Converter<HttpVersion, ConverterInvocation> {

        @Override
        public HttpVersion convert(ConverterInvocation converterInvocation) throws OptionValidatorException {
            if(converterInvocation.getInput() == null)
                return HttpVersion.HTTP_2;
            for(HttpVersion version : HttpVersion.values())
                if(version.name().equals(converterInvocation.getInput()))
                    return version;
            return HttpVersion.HTTP_2;
        }
    }
}
