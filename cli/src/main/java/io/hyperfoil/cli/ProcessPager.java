package io.hyperfoil.cli;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.aesh.command.CommandException;

import io.hyperfoil.cli.context.HyperfoilCommandInvocation;

public class ProcessPager implements Pager {
   private static final String PAGER;
   private final String pager;

   static {
      String pager = System.getenv("PAGER");
      if (pager == null || pager.isEmpty()) {
         pager = CliUtil.fromCommand("update-alternatives", "--display", "pager");
      }
      if (pager == null || pager.isEmpty()) {
         pager = CliUtil.fromCommand("git", "var", "GIT_PAGER");
      }
      if (pager == null || pager.isEmpty()) {
         pager = "less";
      }
      PAGER = pager;
   }

   public ProcessPager(String pager) {
      this.pager = pager;
   }

   @Override
   public void open(HyperfoilCommandInvocation invocation, String text, String prefix, String suffix) throws CommandException {
      File file;
      try {
         file = File.createTempFile(prefix, suffix);
         file.deleteOnExit();
         Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
      } catch (IOException e) {
         throw new CommandException("Cannot create temporary file for edits.", e);
      }
      try {
         open(invocation, file);
      } finally {
         file.delete();
      }
   }

   @Override
   public void open(HyperfoilCommandInvocation invocation, File file) throws CommandException {
      try {
         CliUtil.execProcess(invocation, true, pager == null ? PAGER : pager, file.getPath());
      } catch (IOException e) {
         throw new CommandException("Cannot open file " + file, e);
      }
   }
}
