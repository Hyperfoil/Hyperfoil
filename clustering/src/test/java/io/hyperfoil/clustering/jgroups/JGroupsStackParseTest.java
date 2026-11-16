package io.hyperfoil.clustering.jgroups;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Collectors;

import org.jgroups.JChannel;
import org.jgroups.protocols.FD_ALL3;
import org.jgroups.protocols.FD_SOCK2;
import org.jgroups.protocols.FRAG4;
import org.jgroups.protocols.MERGE3;
import org.jgroups.protocols.MFC;
import org.jgroups.protocols.TCP;
import org.jgroups.protocols.TCPPING;
import org.jgroups.protocols.TP;
import org.jgroups.protocols.UFC;
import org.jgroups.protocols.UNICAST3;
import org.jgroups.protocols.VERIFY_SUSPECT2;
import org.jgroups.protocols.pbcast.GMS;
import org.jgroups.protocols.pbcast.NAKACK2;
import org.jgroups.protocols.pbcast.STABLE;
import org.jgroups.stack.Protocol;
import org.junit.jupiter.api.Test;

/**
 * Parses the shipped stacks without connecting. These are now loaded straight by {@code new JChannel(name)} rather
 * than through Infinispan's {@code <stack-file>}, so a bad edit would otherwise only surface as a flaky
 * clustered integration test.
 */
public class JGroupsStackParseTest {
   private static final List<Class<? extends Protocol>> EXPECTED = List.of(
         TCP.class, TCPPING.class, MERGE3.class, FD_SOCK2.class, FD_ALL3.class, VERIFY_SUSPECT2.class,
         NAKACK2.class, UNICAST3.class, STABLE.class, GMS.class, UFC.class, MFC.class, FRAG4.class);

   @Test
   public void shouldParseControllerStack() throws Exception {
      assertStack("jgroups-tcp.xml");
   }

   @Test
   public void shouldParseAgentStack() throws Exception {
      assertStack("jgroups-tcp-agent.xml");
   }

   private void assertStack(String stackFile) throws Exception {
      try (JChannel channel = new JChannel(stackFile)) {
         TP transport = channel.getProtocolStack().getTransport();
         assertTrue(transport instanceof TCP, "expected a TCP transport, got " + transport.getClass().getName());
         for (Class<? extends Protocol> protocol : EXPECTED) {
            assertNotNull(channel.getProtocolStack().findProtocol(protocol),
                  () -> protocol.getSimpleName() + " missing from " + stackFile + "; stack is "
                        + channel.getProtocolStack().getProtocols().stream().map(p -> p.getClass().getSimpleName())
                              .collect(Collectors.joining(":")));
         }
      }
   }
}
