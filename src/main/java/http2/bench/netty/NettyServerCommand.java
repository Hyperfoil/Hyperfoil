/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package http2.bench.netty;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import http2.bench.ServerCommandBase;
import io.netty.handler.ssl.SslProvider;

/**
 * A HTTP/2 Server that responds to requests with a Hello World. Once started, you can test the
 * server with the example client.
 */
@Parameters()
public final class NettyServerCommand extends ServerCommandBase {

  @Parameter(names = "--open-ssl")
  public boolean openSSL;

  @Parameter(names = "--instances")
  public int instances = 2 * Runtime.getRuntime().availableProcessors();

  public void run() throws Exception {
    Server.run(openSSL ? SslProvider.OPENSSL : SslProvider.JDK, port, instances, soBacklog);
  }
}
