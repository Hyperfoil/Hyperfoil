package io.hyperfoil.core.builders;

import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.hyperfoil.api.config.BaseSequenceBuilder;
import io.hyperfoil.api.config.Ergonomics;
import io.hyperfoil.api.config.Locator;
import io.hyperfoil.api.config.PluginBuilder;
import io.hyperfoil.api.config.ScenarioBuilder;
import io.hyperfoil.api.config.StepBuilder;

public abstract class BaseStepBuilder<T extends BaseStepBuilder<T>> implements StepBuilder<T> {

   private static final Logger log = LogManager.getLogger(BaseStepBuilder.class);

   private BaseSequenceBuilder<?> parent;
   private boolean prepared = false;
   protected boolean useSessionStartTime = false;
   protected int stepId = -1;

   @Override
   public final void prepareBuild() {
      if (prepared) {
         return;
      }
      prepared = true;

      // 1. Evaluate latency compensation FIRST (before doPrepareBuild injects any BeforeSync steps)
      Locator locator = Locator.current();
      Class<? extends PluginBuilder> pClass = pluginClass();

      if (pClass != null) {
         PluginBuilder<?> plugin = locator.benchmark().plugin(pClass);
         if (plugin != null) {
            Ergonomics ergonomics = (Ergonomics) plugin.ergonomics();
            if (ergonomics.compensateInternalLatency()) {
               this.useSessionStartTime = locator.scenario().hasOpenModelPhase()
                     && locator.scenario().isFirstStepInInitialSequence(locator.sequence(), this);
               if (log.isTraceEnabled()) {
                  traceCompensateInternalLatency(locator);
               }
            }
         }
      }
      doPrepareBuild();
      StepBuilder.super.prepareBuild();
   }

   @SuppressWarnings("rawtypes")
   protected Class<? extends PluginBuilder> pluginClass() {
      return null;
   }

   public abstract void doPrepareBuild();

   public T addTo(BaseSequenceBuilder<?> parent) {
      if (this.parent != null) {
         throw new UnsupportedOperationException("Cannot add builder " + getClass().getName() + " to another sequence!");
      }
      parent.stepBuilder(this);
      this.parent = Objects.requireNonNull(parent);
      @SuppressWarnings("unchecked")
      T self = (T) this;
      return self;
   }

   public BaseSequenceBuilder<?> endStep() {
      if (parent == null) {
         throw new UnsupportedOperationException("Sequence for " + getClass().getName() + " was not set.");
      }
      return parent;
   }

   private void traceCompensateInternalLatency(Locator locator) {
      String sequenceName = locator.sequence().name();
      if (this.useSessionStartTime) {
         log.trace("compensateInternalLatency: using session start time for step {} in sequence {}", stepId, sequenceName);
         return;
      }
      ScenarioBuilder scenario = locator.scenario();
      if (!scenario.hasOpenModelPhase()) {
         log.trace("compensateInternalLatency: step {} in sequence {} skipped - phase is not open model", stepId, sequenceName);
      } else if (!scenario.isFirstStepInInitialSequence(locator.sequence(), this)) {
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
