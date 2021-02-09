package io.hyperfoil.http.api;

public enum HttpVersion {
   HTTP_1_0("http/1.0"),
   HTTP_1_1("http/1.1"),
   HTTP_2_0("h2");

   public static final HttpVersion[] ALL_VERSIONS = { HTTP_2_0, HTTP_1_1, HTTP_1_0 };

   public final String protocolName;

   HttpVersion(String protocolName) {
      this.protocolName = protocolName;
   }

   public String protocolName() {
      return protocolName;
   }
}
