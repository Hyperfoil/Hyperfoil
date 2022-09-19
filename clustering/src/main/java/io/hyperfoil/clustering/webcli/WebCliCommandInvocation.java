package io.hyperfoil.clustering.webcli;

import java.util.concurrent.TimeUnit;

import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.invocation.CommandInvocationProvider;
import org.aesh.readline.action.KeyAction;

import io.hyperfoil.cli.context.HyperfoilCommandInvocation;

public class WebCliCommandInvocation extends HyperfoilCommandInvocation {
   public WebCliCommandInvocation(WebCliContext context, CommandInvocation commandInvocation) {
      super(context, commandInvocation);
   }

   @Override
   public WebCliContext context() {
      return (WebCliContext) super.context();
   }

   @Override
   public KeyAction input(long timeout, TimeUnit unit) throws InterruptedException {
      context().outputStream.writeSingleText("__HYPERFOIL_TIMED_INPUT__\n");
      try {
         return super.input(timeout, unit);
      } finally {
         context().outputStream.writeSingleText("__HYPERFOIL_TIMED_INPUT_OFF__\n");
      }
   }

   public static class Provider implements CommandInvocationProvider<WebCliCommandInvocation> {
      private final WebCliContext context;

      public Provider(WebCliContext context) {
         this.context = context;
      }

      @Override
      public WebCliCommandInvocation enhanceCommandInvocation(CommandInvocation invocation) {
         return new WebCliCommandInvocation(context, invocation);
      }
   }
}
