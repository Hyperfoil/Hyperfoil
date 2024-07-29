package io.hyperfoil.core.impl;

import java.util.concurrent.TimeUnit;

import io.netty.util.concurrent.AbstractEventExecutor;
import io.netty.util.concurrent.Future;

public class FakeEventExecutor extends AbstractEventExecutor {
   @Override
   public boolean isShuttingDown() {
      return false;
   }

   @Override
   public Future<?> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {
      return null;
   }

   @Override
   public Future<?> terminationFuture() {
      return null;
   }

   @Override
   public void shutdown() {

   }

   @Override
   public boolean isShutdown() {
      return false;
   }

   @Override
   public boolean isTerminated() {
      return false;
   }

   @Override
   public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
      return false;
   }

   @Override
   public boolean inEventLoop(Thread thread) {
      return false;
   }

   @Override
   public void execute(Runnable command) {

   }
}
