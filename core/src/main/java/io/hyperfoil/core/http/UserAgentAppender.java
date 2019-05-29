package io.hyperfoil.core.http;

import java.net.InetAddress;
import java.net.UnknownHostException;

import io.hyperfoil.api.connection.HttpRequestWriter;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.function.SerializableBiConsumer;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.util.AsciiString;

public class UserAgentAppender implements SerializableBiConsumer<Session, HttpRequestWriter>, ResourceUtilizer, Session.ResourceKey<UserAgentAppender.SessionId> {
   private static final String HOSTNAME;

   static {
      String hostname;
      try {
         hostname = InetAddress.getLocalHost().getHostName();
      } catch (UnknownHostException e) {
         hostname = "unknown";
      }
      HOSTNAME = hostname;
   }

   @Override
   public void accept(Session session, HttpRequestWriter httpRequestWriter) {
      httpRequestWriter.putHeader(HttpHeaderNames.USER_AGENT, session.getResource(this).id);
   }

   @Override
   public void reserve(Session session) {
      session.declareResource(this, new SessionId(new AsciiString("#" + session.uniqueId() + "@" + HOSTNAME)));
   }

   public static final class SessionId implements Session.Resource {
      public final AsciiString id;

      public SessionId(AsciiString id) {
         this.id = id;
      }
   }
}
