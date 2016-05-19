package http2.bench.microservice;

import feign.RequestLine;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface MicroService {

  @RequestLine("GET /")
  String doGet();

  @RequestLine("POST /")
  String doPost(byte[] body);

}
