package io.hyperfoil.api;

import java.io.InputStream;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;

public class Version {
   private static final String VERSION_RESOURCE = "/META-INF/hyperfoil-version.properties";

   public static final String VERSION;
   public static final String COMMIT_ID;

   static {
      String version = "unknown";
      String commitId = "unknown";

      try (InputStream is = Version.class.getResourceAsStream(VERSION_RESOURCE)) {
         if (is != null) {
            Properties properties = new Properties();
            properties.load(is);
            version = properties.getProperty("version", version);
            commitId = properties.getProperty("commit", commitId);
         } else {
            LogManager.getLogger(Version.class).warn("Version resource {} not found", VERSION_RESOURCE);
         }
      } catch (Throwable e) {
         LogManager.getLogger(Version.class).error("Cannot find version info.", e);
      } finally {
         VERSION = version;
         COMMIT_ID = commitId;
      }
   }
}
