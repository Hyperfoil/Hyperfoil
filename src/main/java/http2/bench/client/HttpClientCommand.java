package http2.bench.client;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import http2.bench.CommandBase;
import io.netty.buffer.ByteBuf;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http2.Http2SecurityUtil;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.SupportedCipherSuiteFilter;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.json.JsonObject;

import java.io.File;
import java.io.PrintStream;
import java.net.URI;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Todo : use pool buffers wherever possible
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@Parameters()
public class HttpClientCommand extends CommandBase {

  @Parameter(names = {"-p", "--protocol"})
  HttpVersion protocol = HttpVersion.HTTP_2;

  @Parameter(names = {"-d", "--duration"})
  public String durationParam = "30s";

  @Parameter(names = {"-c", "--connections"})
  public int connections = 1;

  @Parameter(names = {"-o","--out"})
  public String out = null;

  @Parameter(names = {"-b", "--body"})
  public String bodyParam = null;

  @Parameter(names = {"-q", "--max-queue"})
  public int maxQueue = 1;

  @Parameter
  public List<String> uriParam;

  @Parameter(names = {"-r", "--rate"}, variableArity = true)
  public List<Integer> rates = Collections.singletonList(100); // rate per second

  @Parameter(names = {"-w", "--warmup"})
  public String warmupParam = "0";

  @Parameter(names = {"--tags"})
  public String tagString = null;

  private ByteBuf payload;
  private final EventLoopGroup workerGroup = new NioEventLoopGroup();
  private SslContext sslCtx;
  private JsonObject tags = new JsonObject();

  private static long parseDuration(String s) {
    TimeUnit unit;
    String prefix;
    switch (s.charAt(s.length() - 1)) {
      case 's':
        unit = TimeUnit.SECONDS;
        prefix = s.substring(0, s.length() - 1);
        break;
      case 'm':
        unit = TimeUnit.MINUTES;
        prefix = s.substring(0, s.length() - 1);
        break;
      case 'h':
        unit = TimeUnit.HOURS;
        prefix = s.substring(0, s.length() - 1);
        break;
      default:
        unit = TimeUnit.SECONDS;
        prefix = s;
        break;
    }
    return unit.toNanos(Long.parseLong(prefix));
  }

  @Override
  public void run() throws Exception {

    if (tagString != null) {
      for (String tag: tagString.split(",")) {
        String[] components = tag.trim().split("=");
        tags.put(components[0],components[1]);
      }
    }

    long duration = parseDuration(durationParam);
    long warmup = parseDuration(warmupParam);
    tags.put("payloadSize",0);

    if (bodyParam != null) {
      try {
        int size = Integer.parseInt(bodyParam);
        if (size > 0) {
          byte[] bytes = new byte[size];
          Random r = new Random();
          for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) ('A' + r.nextInt(27));
          }
          payload = Buffer.buffer(bytes).getByteBuf();
          tags.put("payloadSize",size);
        }
      } catch (NumberFormatException ignore) {
      }
      if (payload == null) {
        File f = new File(bodyParam);
        if (!f.exists() || !f.isFile()) {
          throw new Exception("could not open file " + bodyParam);
        }
        payload = Buffer.buffer(Files.readAllBytes(f.toPath())).getByteBuf();
      }
    }

    if (uriParam == null || uriParam.size() < 1) {
      throw new Exception("no URI or input file given");
    }
    tags.put("url",uriParam.get(0));

    URI absoluteURI = new URI(uriParam.get(0));
    String host = absoluteURI.getHost();
    int port = absoluteURI.getPort();
    String path = absoluteURI.getPath();

    if (absoluteURI.getScheme().equals("https")) {
      SslProvider provider = OpenSsl.isAlpnSupported() ? SslProvider.OPENSSL : SslProvider.JDK;
      SslContextBuilder builder = SslContextBuilder.forClient()
          .sslProvider(provider)
                /* NOTE: the cipher filter may not include all ciphers required by the HTTP/2 specification.
                 * Please refer to the HTTP/2 specification for cipher requirements. */
          .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
          .trustManager(InsecureTrustManagerFactory.INSTANCE);
      if (protocol == HttpVersion.HTTP_2) {
            builder.applicationProtocolConfig(new ApplicationProtocolConfig(
                ApplicationProtocolConfig.Protocol.ALPN,
                // NO_ADVERTISE is currently the only mode supported by both OpenSsl and JDK providers.
                ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
                // ACCEPT is currently the only mode supported by both OpenSsl and JDK providers.
                ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                ApplicationProtocolNames.HTTP_2,
                ApplicationProtocolNames.HTTP_1_1));
      }
      sslCtx = builder
          .build();
    }

    tags.put("rate",0);
    tags.put("protocol",protocol.toString());
    tags.put("maxQueue",maxQueue);
    tags.put("connections",connections);

    System.out.println("starting benchmark...");
    System.out.format("%d total connections(s)%n", connections);
    StringBuilder ratesChart = new StringBuilder();
    StringBuilder histoChart = new StringBuilder();
    StringBuilder allReport = new StringBuilder();
    Report report = new Report(tags);
    allReport.append(report.header());
    double[] percentiles = { 50, 90, 99, 99.9};
    for (int rate : rates) {
      tags.put("rate",rate);
      Load load = new Load(rate, duration, warmup, protocol, workerGroup, sslCtx, port, host, path, payload, maxQueue, connections, report);
      report = load.run();
      report.prettyPrint();
      if (out != null) {
        report.save(out + "_" + rate);
      }
      ratesChart.append(rate).append(",").append(report.ratio).append("\n");
      histoChart.append(rate);
      for (double percentile : percentiles) {
        histoChart.append(",").append(report.getResponseTimeMillisPercentile(percentile));
      }
      histoChart.append(",").append(report.getMaxResponseTimeMillis());
      histoChart.append("\n");
      allReport.append(report.format(null));
    }

    if (out != null) {
      try (PrintStream ps = new PrintStream(out + "_rates.csv")) {
        ps.print(ratesChart);
      }
      try (PrintStream ps = new PrintStream(out + "_histo.csv")) {
        ps.print(histoChart);
      }
      try (PrintStream ps = new PrintStream(out + "_report.csv")) {
        ps.print(allReport);
      }
    }

    workerGroup.shutdownGracefully(0, 10, TimeUnit.SECONDS);
  }
}
