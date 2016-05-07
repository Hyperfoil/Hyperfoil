package http2.bench.jfr;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import java.lang.management.ManagementFactory;

/**
 * Basic API to interact with JFR using the DiagnosticCommand MBean.
 *
 * @See com.sun.management.DiagnosticCommandMBean
 */
class DiagnosticCommand {

  private final MBeanServer mBeanServer;
  private final ObjectName objectName;

  DiagnosticCommand() throws DiagnosticCommandBeanNotAvailable {
    this.mBeanServer = ManagementFactory.getPlatformMBeanServer();
    this.objectName = getObjectName();
  }

  private ObjectName getObjectName() throws DiagnosticCommandBeanNotAvailable {
    try {
      return new ObjectName("com.sun.management:type=DiagnosticCommand");
    } catch (MalformedObjectNameException e) {
      throw new DiagnosticCommandBeanNotAvailable();
    }
  }

  // mBeanServer's exceptions are converted into undocumented runtime exceptions.
  // Since the JVM version has been checked earlier, we know that theses operations won't fail.

  public void unlockCommercialFeatures() {
    try {
      mBeanServer.invoke(objectName, "vmUnlockCommercialFeatures", null, null);
    } catch (InstanceNotFoundException | MBeanException | ReflectionException e) {
      throw new JfrRecordingError(e);
    }
  }

  public void jfrStart(String... args) {
    try {
      Object[] argsObj = new Object[]{args};
      mBeanServer.invoke(objectName, "jfrStart", argsObj, StringArraySignature());
    } catch (InstanceNotFoundException | MBeanException | ReflectionException e) {
      throw new JfrRecordingError(e);
    }
  }

  public String jfrStop(String... args) {
    try {
      Object[] argsObj = new Object[]{args};
      Object ret = mBeanServer.invoke(objectName, "jfrStop", argsObj, StringArraySignature());
      return (String) ret;
    } catch (InstanceNotFoundException | MBeanException | ReflectionException e) {
      throw new JfrRecordingError(e);
    }
  }

  private String[] StringArraySignature() {
    return new String[]{String[].class.getName()};
  }
}