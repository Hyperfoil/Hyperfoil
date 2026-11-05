package io.hyperfoil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.InetAddress;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.MountableFile;

/**
 * Reproduces the case where the controller's hostname resolves to a loopback address that is not literally
 * {@code 127.0.0.1} - typically {@code 127.0.1.1}. Before the fix the
 * loopback guard in {@link Hyperfoil#clusteredVertx(boolean)} only matched {@code 127.0.0.1}/{@code ::1}, so the
 * controller happily advertised {@code 127.0.1.1} to the agents and remote agents could never connect
 * ("Deployment timed out").
 */
public class ControllerClusterIpTest {
   private static final String IMAGE = "eclipse-temurin:21";
   private static final String HOSTNAME = "hyperfoil-controller";
   private static final String CLASSPATH = "/cp";

   @Test
   public void shouldNotAdvertiseLoopbackWhenHostnameResolvesTo() throws Exception {
      assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is not available");

      try (GenericContainer<?> container = new GenericContainer<>(IMAGE)
            .withCreateContainerCmdModifier(cmd -> cmd.withHostName(HOSTNAME))
            .withExtraHost(HOSTNAME, "127.0.1.1")
            // keep the container alive; the JVM is started via exec below
            .withCommand("sleep", "infinity")) {
         // Both classes depend on nothing but the JDK, so these two files are the entire classpath.
         copyClass(container, ClusterAddressResolver.class);
         copyClass(container, ResolveControllerAddressMain.class);
         container.start();

         Container.ExecResult result = container.execInContainer(
               "java", "-cp", CLASSPATH, ResolveControllerAddressMain.class.getName());
         String logs = result.getStdout() + result.getStderr();
         assertEquals(0, result.getExitCode(), "resolution failed:\n" + logs);

         assertEquals("127.0.1.1", value(logs, ResolveControllerAddressMain.LOCAL_HOST_PREFIX),
               "expected the container hostname to resolve to 127.0.1.1, logs:\n" + logs);

         String resolved = value(logs, ResolveControllerAddressMain.RESOLVED_PREFIX);
         assertFalse(InetAddress.getByName(resolved).isLoopbackAddress(),
               "controller would advertise loopback address " + resolved + " to agents, logs:\n" + logs);
         String containerIp = container.getContainerInfo().getNetworkSettings().getNetworks().values()
               .iterator().next().getIpAddress();
         assertEquals(containerIp, resolved, "expected the address of the container's network interface");
      }
   }

   @Test
   public void shouldHonourExplicitClusterIp() throws Exception {
      assertEquals("10.1.2.3", ClusterAddressResolver.resolve("10.1.2.3").getHostAddress());
   }

   @Test
   public void shouldResolveNonLoopbackAddressOnThisMachine() throws Exception {
      assumeTrue(InetAddress.getLocalHost().isLoopbackAddress(),
            "this machine's hostname does not resolve to loopback, nothing to fall back from");
      InetAddress resolved = ClusterAddressResolver.resolve(null);
      assertTrue(!resolved.isLoopbackAddress() || resolved.equals(InetAddress.getLocalHost()),
            "either a real interface address, or loopback if the machine genuinely has no other interface");
   }

   /** Copies the compiled .class file into {@link #CLASSPATH}, keeping the package layout the classloader expects. */
   private static void copyClass(GenericContainer<?> container, Class<?> clazz) {
      String resource = clazz.getName().replace('.', '/') + ".class";
      URL url = ControllerClusterIpTest.class.getClassLoader().getResource(resource);
      if (url == null) {
         throw new AssertionError("Cannot find " + resource + " on the test classpath");
      }
      try {
         container.withCopyFileToContainer(MountableFile.forHostPath(Paths.get(url.toURI())),
               CLASSPATH + "/" + resource);
      } catch (URISyntaxException e) {
         throw new AssertionError("Cannot read " + url, e);
      }
   }

   private static String value(String logs, String prefix) {
      return Arrays.stream(logs.split("\n")).map(String::trim).filter(l -> l.startsWith(prefix))
            .map(l -> l.substring(prefix.length())).findFirst()
            .orElseThrow(() -> new AssertionError("No line starting with '" + prefix + "' in:\n" + logs));
   }
}
