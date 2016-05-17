package http2.bench.client;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class DataFrame {

  final boolean end;

  public DataFrame(boolean end) {
    this.end = end;
  }
}
