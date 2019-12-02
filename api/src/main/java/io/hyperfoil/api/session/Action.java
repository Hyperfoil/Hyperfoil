package io.hyperfoil.api.session;

import java.io.Serializable;

import io.hyperfoil.api.config.BuilderBase;

/**
 * Actions are similar to {@link io.hyperfoil.api.config.Step steps} but are executed unconditionally;
 * Action cannot block sequence execution.
 */
public interface Action extends Serializable {
   void run(Session session);

   interface Builder extends BuilderBase<Builder> {
      Action build();
   }
}
