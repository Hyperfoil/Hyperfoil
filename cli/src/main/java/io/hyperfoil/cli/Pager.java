package io.hyperfoil.cli;

import java.io.File;

import org.aesh.command.CommandException;

import io.hyperfoil.cli.context.HyperfoilCommandInvocation;

public interface Pager {
   void open(HyperfoilCommandInvocation invocation, String text, String prefix, String suffix) throws CommandException;

   void open(HyperfoilCommandInvocation invocation, File file) throws CommandException;
}
