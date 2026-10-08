package io.hyperfoil.deploy.ssh;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.sshd.client.session.ClientSession;

/**
 * Agent used only by the test suite
 */
public class TestSshDeployedAgent extends SshDeployedAgent {
   private static final Logger log = LogManager.getLogger(TestSshDeployedAgent.class);
   private static final Set<String> BUILD_OUTPUT_DIRS = Set.of("target", "build", "classes", "test-classes",
         "main", "test", "java", "kotlin", "resources");
   /**
    * A fixed timestamp (along with sorted files) ensures the JAR's MD5 changes
    * only when class contents change. Otherwise, JarEntry uses the current time,
    * altering the MD5 and forcing redundant SCP uploads on every run.
    */
   private static final long FIXED_ENTRY_TIME = 946684800000L; // 2000-01-01T00:00:00Z

   private Path tempJarDir;

   public TestSshDeployedAgent(String name, String runId, String username, String hostname, String sshKey, int port,
         String dir, String extras, String cpu) {
      super(name, runId, username, hostname, sshKey, port, dir, extras, cpu);
   }

   @Override
   public void deploy(ClientSession session, Consumer<Throwable> exceptionHandler) {
      try {
         super.deploy(session, exceptionHandler);
      } finally {
         deleteTempJarDir();
      }
   }

   @Override
   protected String uploadableFile(String classpathEntry) {
      String jar = super.uploadableFile(classpathEntry);
      if (jar != null) {
         return jar;
      }
      Path directory = Path.of(classpathEntry);
      if (!Files.isDirectory(directory)) {
         return null;
      }
      return packDirectory(directory).getAbsolutePath();
   }

   private File packDirectory(Path directory) {
      File jar;
      try {
         if (tempJarDir == null) {
            tempJarDir = Files.createTempDirectory("hyperfoil-agentlib-");
         }
         jar = tempJarDir.resolve(jarName(directory)).toFile();
         List<Path> files;
         try (Stream<Path> walk = Files.walk(directory)) {
            files = walk.filter(Files::isRegularFile).sorted().collect(Collectors.toList());
         }
         try (JarOutputStream out = new JarOutputStream(new FileOutputStream(jar))) {
            for (Path file : files) {
               JarEntry entry = new JarEntry(directory.relativize(file).toString().replace(File.separatorChar, '/'));
               entry.setTime(FIXED_ENTRY_TIME);
               out.putNextEntry(entry);
               Files.copy(file, out);
               out.closeEntry();
            }
         }
      } catch (IOException e) {
         throw new IllegalStateException("Cannot pack classpath directory " + directory, e);
      }
      log.debug("Packed classpath directory {} into {}", directory, jar);
      return jar;
   }

   private static String jarName(Path directory) {
      Path absolute = directory.toAbsolutePath();
      Path hint = absolute;
      while (hint.getParent() != null && BUILD_OUTPUT_DIRS.contains(hint.getFileName().toString())) {
         hint = hint.getParent();
      }
      return hint.getFileName() + "-" + Integer.toHexString(absolute.toString().hashCode()) + ".jar";
   }

   private void deleteTempJarDir() {
      if (tempJarDir == null) {
         return;
      }
      try (Stream<Path> walk = Files.walk(tempJarDir)) {
         for (Path path : walk.sorted(Comparator.reverseOrder()).collect(Collectors.toList())) {
            Files.deleteIfExists(path);
         }
      } catch (IOException e) {
         log.debug("Could not delete temporary directory {}", tempJarDir, e);
      }
      tempJarDir = null;
   }
}
