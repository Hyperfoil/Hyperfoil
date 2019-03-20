package io.hyperfoil.impl;

import io.hyperfoil.api.config.BaseSequenceBuilder;
import io.hyperfoil.api.config.Step;

public interface StepCatalogFactory {
   Step.Catalog create(BaseSequenceBuilder sequenceBuilder);
}
