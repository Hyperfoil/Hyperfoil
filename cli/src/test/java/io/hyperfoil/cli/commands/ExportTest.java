package io.hyperfoil.cli.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.aesh.command.shell.Shell;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import io.hyperfoil.cli.context.HyperfoilCliContext;
import io.hyperfoil.cli.context.HyperfoilCommandInvocation;
import io.hyperfoil.client.RestClient;
import io.hyperfoil.controller.Client;

@EnabledOnOs(OS.LINUX)
public class ExportTest {

   @Test
   public void tildeInDestinationIsExpandedToHomeDirectory() throws Exception {
      // "~/testrun.json" expanded to the real home dir.
      Path expected = Paths.get(System.getProperty("user.home"), "testrun.json");

      Export export = newExport("~/testrun.json", true);
      Client.RunRef runRef = mockRunRef();
      HyperfoilCliContext ctx = mockConnectedContext(runRef);
      HyperfoilCommandInvocation invocation = mockInvocation(ctx, mock(Shell.class));

      // Fakes java.nio.file.Files, no real disk access.
      try (MockedStatic<Files> files = mockStatic(Files.class)) {
         // Skip the directory check and the overwrite prompt.
         files.when(() -> Files.isDirectory(any(Path.class))).thenReturn(false);
         files.when(() -> Files.exists(any(Path.class))).thenReturn(false);

         export.execute(invocation);

         // written.capture() matches any Path and also records the one actually used.
         ArgumentCaptor<Path> written = ArgumentCaptor.forClass(Path.class);
         files.verify(() -> Files.write(written.capture(), any(byte[].class)));
         // Was "~/" really expanded
         assertEquals(expected, written.getValue(),
               "destination starting with ~/ must be expanded to the user's home directory");
      }
   }

   @Test
   public void doesNotPromptWhenTargetFileInsideExistingDirectoryIsMissing() throws Exception {
      // export -d /some/dir
      Export export = newExport("/some/dir", false);
      Client.RunRef runRef = mockRunRef();
      HyperfoilCliContext ctx = mockConnectedContext(runRef);
      Shell shell = mock(Shell.class);
      // If Export wrongly prompts "overwrite?", it reads a line here. We make it
      // return "n", so the bug shows as "export silently cancelled" instead of a mock with no configured answer.
      // this is to reproduce the error without the fix
      when(shell.readLine()).thenReturn("n");
      HyperfoilCommandInvocation invocation = mockInvocation(ctx, shell);

      Path dir = Paths.get("/some/dir");
      Path targetFile = Paths.get("/some/dir/testrun.json");
      try (MockedStatic<Files> files = mockStatic(Files.class)) {
         // dir exists ...
         files.when(() -> Files.isDirectory(dir)).thenReturn(true);
         files.when(() -> Files.exists(dir)).thenReturn(true);
         // ... but the real target file inside it does not exist yet.
         files.when(() -> Files.exists(targetFile)).thenReturn(false);

         export.execute(invocation);

         // Expected: write directly, no overwrite prompt.
         files.verify(() -> Files.write(any(Path.class), any(byte[].class)));
         // The target file does not exist, so no prompt is expected.
         verify(shell, never()).readLine();
      }
   }

   private Export newExport(String destination, boolean assumeYes) throws Exception {
      Export export = new Export();
      export.destination = destination;
      export.assumeYes = assumeYes;
      export.format = "JSON";
      // runId is a private field on BaseRunIdCommand; leaving it null makes Export fall back to
      // HyperfoilCliContext#serverRun(), which we stub below.
      Field runId = BaseRunIdCommand.class.getDeclaredField("runId");
      runId.setAccessible(true);
      runId.set(export, null);
      return export;
   }

   private HyperfoilCommandInvocation mockInvocation(HyperfoilCliContext ctx, Shell shell) {
      HyperfoilCommandInvocation invocation = mock(HyperfoilCommandInvocation.class);
      when(invocation.context()).thenReturn(ctx);
      when(invocation.getShell()).thenReturn(shell);
      return invocation;
   }

   private HyperfoilCliContext mockConnectedContext(Client.RunRef runRef) {
      HyperfoilCliContext ctx = mock(HyperfoilCliContext.class);
      when(ctx.client()).thenReturn(mock(RestClient.class));
      when(ctx.serverRun()).thenReturn(runRef);
      return ctx;
   }

   private Client.RunRef mockRunRef() throws Exception {
      Client.RunRef runRef = mock(Client.RunRef.class);
      when(runRef.id()).thenReturn("testrun");
      when(runRef.statsAll(anyString())).thenReturn(new byte[] { 1, 2, 3 });
      return runRef;
   }
}
