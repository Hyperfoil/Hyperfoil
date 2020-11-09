package io.hyperfoil.core.handlers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.BenchmarkDefinitionException;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.config.PartialBuilder;
import io.hyperfoil.api.connection.HttpRequest;
import io.hyperfoil.api.http.StatusHandler;
import io.hyperfoil.api.session.Action;
import io.hyperfoil.api.session.ResourceUtilizer;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.builders.ServiceLoadedBuilderProvider;

// Note: maybe it would be better to just use multiplex and let actions convert to status handlers?
public class ActionStatusHandler extends BaseRangeStatusHandler implements ResourceUtilizer {
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

   @Override
   public void reserve(Session session) {
      for (Action[] as : actions) {
         ResourceUtilizer.reserve(session, (Object[]) as);
      }
      ResourceUtilizer.reserve(session, (Object[]) otherActions);
   }

   /**
    * Perform certain actions when the status falls into a range.
    */
   @MetaInfServices(StatusHandler.Builder.class)
   @Name("action")
   public static class Builder implements StatusHandler.Builder, PartialBuilder {
      private final Map<String, List<Action.Builder>> actions = new HashMap<>();

      /**
       * Perform a sequence of actions if the range matches.
       *
       * @param range Possible values of the status separated by commas (,). Ranges can be set using low-high (inclusive) (e.g. 200-299), or replacing lower digits with 'x' (e.g. 2xx).
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
         Action[] otherActions = checkAndSortRanges(this.actions, ranges, actions, list -> list.stream().map(Action.Builder::build).toArray(Action[]::new));
         return new ActionStatusHandler(ranges.stream().mapToInt(Integer::intValue).toArray(), actions.toArray(new Action[0][]), otherActions);
      }
   }
}
