package io.hyperfoil.api.session;

import java.io.Serializable;

import io.hyperfoil.api.config.ServiceLoadedBuilder;

/**
 * Actions are similar to {@link io.hyperfoil.api.config.Step steps} but are executed unconditionally;
 * Action cannot block sequence execution.
 */
public interface Action extends Serializable {
   void run(Session session);

   interface BuilderFactory extends ServiceLoadedBuilder.Factory<Action> {}

   /**
    * Combination of an action and a step.
    */
   interface Step extends Action, io.hyperfoil.api.config.Step {
      @Override
      default boolean invoke(Session session) {
         run(session);
         return true;
      }
   }
}
