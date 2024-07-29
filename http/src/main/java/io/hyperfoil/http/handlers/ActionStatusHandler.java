package io.hyperfoil.http.handlers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.config.PartialBuilder;
import io.hyperfoil.api.session.Action;
import io.hyperfoil.core.builders.ServiceLoadedBuilderProvider;
import io.hyperfoil.http.api.HttpRequest;
import io.hyperfoil.http.api.StatusHandler;

// Note: maybe it would be better to just use multiplex and let actions convert to status handlers?
public class ActionStatusHandler extends BaseRangeStatusHandler {
   private final Action[][] actions;
   private final Action[] otherActions;

   public ActionStatusHandler(int[] statusRanges, Action[][] actions, Action[] otherActions) {
      super(statusRanges);
      assert statusRanges.length == 2 * actions.length;
      this.actions = actions;
      this.otherActions = otherActions;
   }

   @Override
   protected void onStatusRange(HttpRequest request, int status, int index) {
      for (Action a : actions[index]) {
         a.run(request.session);
      }
   }

   @Override
   protected void onOtherStatus(HttpRequest request, int status) {
      if (otherActions != null) {
         for (Action a : otherActions) {
            a.run(request.session);
         }
      }
   }

   /**
    * Perform certain actions when the status falls into a range.
    */
   @MetaInfServices(StatusHandler.Builder.class)
   @Name("action")
   public static class Builder implements StatusHandler.Builder, PartialBuilder {
      private Map<String, List<Action.Builder>> actions = new HashMap<>();

      /**
       * Perform a sequence of actions if the range matches. Use range as the key and action in the mapping.
       * Possible values of the status should be separated by commas (,). Ranges can be set using low-high (inclusive) (e.g.
       * 200-299), or replacing lower digits with 'x' (e.g. 2xx).
       *
       * @param range Status range.
       * @return Builder
       */
      @Override
      public ServiceLoadedBuilderProvider<Action.Builder> withKey(String range) {
         List<Action.Builder> actions = new ArrayList<>();
         add(range, actions);
         return new ServiceLoadedBuilderProvider<>(Action.Builder.class, actions::add);
      }

      public Builder add(String range, List<Action.Builder> actions) {
         if (this.actions.putIfAbsent(range, actions) != null) {
            throw new BenchmarkDefinitionException("Range '" + range + "' is already set.");
         }
         return this;
      }

      @Override
      public ActionStatusHandler build() {
         List<Integer> ranges = new ArrayList<>();
         List<Action[]> actions = new ArrayList<>();
         Action[] otherActions = checkAndSortRanges(this.actions, ranges, actions,
               list -> list.stream().map(Action.Builder::build).toArray(Action[]::new));
         return new ActionStatusHandler(ranges.stream().mapToInt(Integer::intValue).toArray(), actions.toArray(new Action[0][]),
               otherActions);
      }
   }
}
