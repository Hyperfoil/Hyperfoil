package io.hyperfoil.core.handlers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import io.hyperfoil.api.session.Action;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.builders.ServiceLoadedBuilderProvider;

public abstract class BaseDelegatingAction implements Action, ResourceUtilizer {
   protected final Action[] actions;

   public BaseDelegatingAction(Action[] actions) {
      this.actions = actions;
   }

   @Override
   public void run(Session session) {
      for (Action a : actions) {
         a.run(session);
      }
   }

   @Override
   public void reserve(Session session) {
      ResourceUtilizer.reserve(session, (Object[]) actions);
   }

   public abstract static class Builder<S extends Builder<S>> implements Action.Builder {
      protected final List<Action.Builder> actions = new ArrayList<>();

      @SuppressWarnings("unchecked")
      protected S self() {
         return (S) this;
      }

      public S action(Action.Builder action) {
         this.actions.add(action);
         return self();
      }


      public S actions(Collection<? extends Action.Builder> actions) {
         this.actions.addAll(actions);
         return self();
      }

      /**
       * Actions that should be executed should the condition hold.
       *
       * @return Builder.
       */
      public ServiceLoadedBuilderProvider<Action.Builder> actions() {
         return new ServiceLoadedBuilderProvider<>(Action.Builder.class, actions::add);
      }

      protected Action[] buildActions() {
         return actions.stream().map(Action.Builder::build).toArray(Action[]::new);
      }
   }
}
