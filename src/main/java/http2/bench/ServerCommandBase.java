package http2.bench;

import com.beust.jcommander.Parameter;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class ServerCommandBase extends CommandBase {

  @Parameter(names = "--clear-text")
  public boolean clearText;

  @Parameter(names = "--port")
  public int port = 8443;

  @Parameter(names = "--backend")
  public Backend backend = Backend.NONE;

  @Parameter(names = "--so-backlog")
  public int soBacklog = 1024;

  @Parameter(names = "--pool-size")
  public int poolSize = 32;

  @Parameter(names = "--delay")
  public int delay = 0;

  @Parameter(names = "--backend-host")
  public String backendHost = "localhost";

  @Parameter(names = "--backend-port")
  public int backendPort = 8080;
}
