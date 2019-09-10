package io.hyperfoil.core;

import java.net.URL;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import org.slf4j.LoggerFactory;

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
            String cp = attr.getValue("Class-Path");
            int apiIndex = cp.indexOf("hyperfoil-api-");
            if (apiIndex >= 0) {
               int jarIndex = cp.indexOf(".jar", apiIndex + 14);
               version = cp.substring(apiIndex + 14, jarIndex < 0 ? cp.length() : jarIndex);
            }
         }
      } catch (Throwable e) {
         LoggerFactory.getLogger(Version.class).error("Cannot find version info.", e);
      } finally {
         VERSION = version;
         COMMIT_ID = commitId;

      }
   }
}
