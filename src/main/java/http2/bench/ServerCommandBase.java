package http2.bench;

import com.beust.jcommander.Parameter;

import java.util.Collections;
import java.util.List;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class ServerCommandBase extends CommandBase {

  @Parameter(names = "--clear-text")
  public boolean clearText;

  @Parameter(names = "--port")
  public int port = 8443;

  @Parameter(names = "--backend")
  public BackendType backend = BackendType.NONE;

  @Parameter(names = "--so-backlog")
  public int soBacklog = 1024;

  @Parameter(names = "--pool-size")
  public int poolSize = 32;

  @Parameter(names = "--delay", description = "the delay in ms for sending the response, it can be a percentile distribution, e.g 5,20,...")
  public List<Long> delay = Collections.singletonList(0L);

  @Parameter(names = "--backend-host")
  public String backendHost = "localhost";

  @Parameter(names = "--backend-port")
  public int backendPort = 8080;
}
