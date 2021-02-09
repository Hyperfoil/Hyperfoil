package io.hyperfoil.impl;

import io.hyperfoil.api.config.BaseSequenceBuilder;
import io.hyperfoil.api.config.Step;

public interface StepCatalogFactory {
   Class<? extends Step.Catalog> clazz();

   Step.Catalog create(BaseSequenceBuilder sequenceBuilder);
}
