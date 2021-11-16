package io.hyperfoil.api.session;

/**
 * Data structure that holds immutable data with push-once semantics.
 */
public interface AgentData {
   void push(Session session, String name, Object object);

   void pull(Session session, String name, ObjectAccess access);
}
