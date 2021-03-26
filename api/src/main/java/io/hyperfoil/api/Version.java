package io.hyperfoil.api;

import java.net.URL;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import org.apache.logging.log4j.LogManager;

public class Version {
   public static final String VERSION;
   public static final String COMMIT_ID;

   static {
      String version = "unknown";
      String commitId = "unknown";
      try {
         String classPath = Version.class.getResource(Version.class.getSimpleName() + ".class").toString();
         if (classPath.startsWith("jar")) {
            String manifestPath = classPath.substring(0, classPath.lastIndexOf("!") + 1) +
                  "/META-INF/MANIFEST.MF";
            Manifest manifest = null;
            manifest = new Manifest(new URL(manifestPath).openStream());
            Attributes attr = manifest.getMainAttributes();
            commitId = attr.getValue("Scm-Revision");
            version = attr.getValue("Implementation-Version");
         }
      } catch (Throwable e) {
         LogManager.getLogger(Version.class).error("Cannot find version info.", e);
      } finally {
         VERSION = version;
         COMMIT_ID = commitId;
      }
   }
}
