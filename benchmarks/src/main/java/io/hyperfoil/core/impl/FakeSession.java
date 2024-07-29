package io.hyperfoil.core.impl;

import java.util.function.Supplier;

import io.hyperfoil.api.config.Phase;
import io.hyperfoil.api.config.Scenario;
import io.hyperfoil.api.connection.Request;
import io.hyperfoil.api.session.AgentData;
import io.hyperfoil.api.session.GlobalData;
import io.hyperfoil.api.session.PhaseInstance;
import io.hyperfoil.api.session.SequenceInstance;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.api.session.ThreadData;
import io.hyperfoil.api.statistics.SessionStatistics;
import io.hyperfoil.api.statistics.Statistics;
import io.netty.util.concurrent.EventExecutor;

public class FakeSession implements Session {

   private final EventExecutor executor;
   private final int agentThreadId;

   public FakeSession(EventExecutor executor, int agentThreadId) {
      this.executor = executor;
      this.agentThreadId = agentThreadId;
   }

   @Override
   public Runnable runTask() {
      return null;
   }

   @Override
   public void reserve(Scenario scenario) {

   }

   @Override
   public int uniqueId() {
      return 0;
   }

   @Override
   public int agentThreadId() {
      return agentThreadId;
   }

   @Override
   public int agentThreads() {
      return 0;
   }

   @Override
   public int globalThreadId() {
      return 0;
   }

   @Override
   public int globalThreads() {
      return 0;
   }

   @Override
   public int agentId() {
      return 0;
   }

   @Override
   public int agents() {
      return 0;
   }

   @Override
   public String runId() {
      return "";
   }

   @Override
   public EventExecutor executor() {
      return executor;
   }

   @Override
   public ThreadData threadData() {
      return null;
   }

   @Override
   public AgentData agentData() {
      return null;
   }

   @Override
   public GlobalData globalData() {
      return null;
   }

   @Override
   public PhaseInstance phase() {
      return null;
   }

   @Override
   public long phaseStartTimestamp() {
      return 0;
   }

   @Override
   public Statistics statistics(int stepId, String name) {
      return null;
   }

   @Override
   public void pruneStats(Phase phase) {

   }

   @Override
   public <R extends Resource> void declareResource(ResourceKey<R> key, Supplier<R> resourceSupplier) {

   }

   @Override
   public <R extends Resource> void declareResource(ResourceKey<R> key, Supplier<R> resourceSupplier, boolean singleton) {

   }

   @Override
   public <R extends Resource> void declareSingletonResource(ResourceKey<R> key, R resource) {

   }

   @Override
   public <R extends Resource> R getResource(ResourceKey<R> key) {
      return null;
   }

   @Override
   public void currentSequence(SequenceInstance current) {

   }

   @Override
   public SequenceInstance currentSequence() {
      return null;
   }

   @Override
   public void attach(EventExecutor executor, ThreadData threadData, AgentData agentData, GlobalData globalData,
         SessionStatistics statistics) {

   }

   @Override
   public void start(PhaseInstance phase) {

   }

   @Override
   public void proceed() {

   }

   @Override
   public void reset() {

   }

   @Override
   public SequenceInstance startSequence(String name, boolean forceSameIndex, ConcurrencyPolicy policy) {
      return null;
   }

   @Override
   public void stop() {

   }

   @Override
   public void fail(Throwable t) {

   }

   @Override
   public boolean isActive() {
      return false;
   }

   @Override
   public Request currentRequest() {
      return null;
   }

   @Override
   public void currentRequest(Request request) {

   }
}
