package http2.bench;

import com.beust.jcommander.Parameter;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class ServerCommandBase extends CommandBase {

  @Parameter(names = "--port")
  public int port = 8443;

  @Parameter(names = "--backend")
  public Backend backend = Backend.NONE;

  @Parameter(names = "--so-backlog")
  public int soBacklog = 1024;

  @Parameter(names = "--db-pool-size")
  public int dbPoolSize = 32;

  @Parameter(names = "--sleep-time")
  public int sleepTime = 0;
}
