package io.hyperfoil.cli.commands;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

import io.hyperfoil.cli.context.HyperfoilCommandInvocation;

public class OutputCommandUtilTest {

   @Test
   public void rootPathDoesNotCrashAndFallsBackToHtml() throws Exception {
      // Path.of("/").getFileName() is null: there is no file name to parse an extension from.
      // Not throwing here (e.g. a NullPointerException) is itself the assertion.
      HyperfoilCommandInvocation invocation = mock(HyperfoilCommandInvocation.class);

      OutputCommandUtil.executeOutputCommand(invocation, "/", true);

      verify(invocation).executeSwitchable("report --silent -y --destination /");
   }
}
