package http2.bench;

import com.beust.jcommander.Parameter;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class ServerBase {

  @Parameter(names = "--help", help = true)
  public boolean help;

  @Parameter(names = "--http-port")
  public int httpPort = 8080;

  @Parameter(names = "--https-port")
  public int httpsPort = 8443;

  @Parameter(names = "--backend")
  public Backend backend = Backend.NONE;

  public abstract void run() throws Exception;

}
