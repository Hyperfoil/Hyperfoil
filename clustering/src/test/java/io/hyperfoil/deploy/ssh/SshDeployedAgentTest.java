package io.hyperfoil.deploy.ssh;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.sshd.client.session.ClientSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The controller can stop the same agent twice: once when the run ends while the agent is still deploying
 * and again when the deployer returns late.
 */
public class SshDeployedAgentTest {

   @Test
   public void testStopIsIdempotent(@TempDir Path dir) throws Exception {
      SshDeployedAgent agent = new SshDeployedAgent("test-agent", "run-123", "user", "localhost", null, 22, dir.toString(),
            null,
            null);

      AtomicInteger sessionCloses = new AtomicInteger();
      AtomicInteger commandStreamCloses = new AtomicInteger();
      set(agent, "session", closeCounting(ClientSession.class, sessionCloses));
      set(agent, "commandStream", new PrintStream(new OutputStream() {
         @Override
         public void write(int b) {
         }

         @Override
         public void close() {
            commandStreamCloses.incrementAndGet();
         }
      }));

      assertDoesNotThrow(() -> {
         agent.stop();
         agent.stop();
      });

      // PrintStream swallows the repeated close, so the command stream is closed exactly once
      assertEquals(1, commandStreamCloses.get());
      // The SSHD session is closed on every call; SSHD close() is idempotent. The shell channel is left unset: stop() skips it when null.
      assertEquals(2, sessionCloses.get());
   }

   private static <T> T closeCounting(Class<T> type, AtomicInteger closes) {
      return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, (proxy, method, args) -> {
         if (method.getName().equals("close") && (args == null || args.length == 0)) {
            closes.incrementAndGet();
         }
         return null;
      }));
   }

   private static void set(Object target, String field, Object value) throws ReflectiveOperationException {
      Field f = SshDeployedAgent.class.getDeclaredField(field);
      f.setAccessible(true);
      f.set(target, value);
   }
}
