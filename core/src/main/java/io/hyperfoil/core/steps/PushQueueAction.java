package io.hyperfoil.core.steps;

import io.hyperfoil.api.session.Access;
import io.hyperfoil.api.session.Action;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.data.Queue;

public class PushQueueAction implements Action {
   private final Access fromVar;
   private final Queue.Key queueKey;

   public PushQueueAction(Access fromVar, Queue.Key queueKey) {
      this.fromVar = fromVar;
      this.queueKey = queueKey;
   }

   @Override
   public void run(Session session) {
      session.getResource(queueKey).push(session, fromVar.getObject(session));
   }
}
