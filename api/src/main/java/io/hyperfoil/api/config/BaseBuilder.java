package io.hyperfoil.api.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class BaseBuilder {

   private static final Logger log = LogManager.getLogger(BaseBuilder.class);

   protected int stepId = -1;
   protected boolean useSessionStartTime = false;

   protected void prepare(Class<? extends PluginBuilder> pClass, Object obj) {
      Locator locator = Locator.current();
      if (pClass != null) {
         PluginBuilder<?> plugin = locator.benchmark().plugin(pClass);
         if (plugin != null) {
            Ergonomics ergonomics = (Ergonomics) plugin.ergonomics();
            if (ergonomics.compensateInternalLatency()) {
               this.useSessionStartTime = locator.scenario().hasOpenModelPhase()
                     && locator.scenario().isFirstStepInInitialSequence(locator.sequence(), (StepBuilder) obj);
               if (log.isTraceEnabled()) {
                  traceCompensateInternalLatency(locator, obj);
               }
            }
         }
      }
   }

   private void traceCompensateInternalLatency(Locator locator, Object obj) {
      String sequenceName = locator.sequence().name();
      if (this.useSessionStartTime) {
         log.trace("compensateInternalLatency: using session start time for step {} in sequence {}", stepId, sequenceName);
         return;
      }
      ScenarioBuilder scenario = locator.scenario();
      if (!scenario.hasOpenModelPhase()) {
         log.trace("compensateInternalLatency: step {} in sequence {} skipped - phase is not open model", stepId, sequenceName);
      } else if (!scenario.isFirstStepInInitialSequence(locator.sequence(), (StepBuilder) obj)) {
         if (!scenario.isInitialSequence(locator.sequence())) {
            log.trace("compensateInternalLatency: step {} in sequence {} skipped - not an initial sequence", stepId,
                  sequenceName);
         } else {
            log.trace("compensateInternalLatency: step {} in sequence {} skipped - not the first step in the sequence", stepId,
                  sequenceName);
         }
      }
   }
}
