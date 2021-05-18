package io.hyperfoil.core.steps;

import io.hyperfoil.api.session.Action;
import io.hyperfoil.api.session.ReadAccess;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.data.Queue;

public class PushQueueAction implements Action {
   private final ReadAccess fromVar;
   private final Queue.Key queueKey;

   public PushQueueAction(ReadAccess fromVar, Queue.Key queueKey) {
      this.fromVar = fromVar;
      this.queueKey = queueKey;
   }

   @Override
   public void run(Session session) {
      session.getResource(queueKey).push(session, fromVar.getObject(session));
   }
}
