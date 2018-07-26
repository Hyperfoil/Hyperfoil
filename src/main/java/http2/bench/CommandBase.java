package http2.bench;

import com.beust.jcommander.Parameter;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class CommandBase {

  @Parameter(names = "--help", help = true)
  public boolean help;

  public abstract void run() throws Exception;

}
