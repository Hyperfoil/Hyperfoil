package io.hyperfoil.core.session;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import io.hyperfoil.api.BenchmarkExecutionException;
import io.hyperfoil.api.session.AgentData;
import io.hyperfoil.api.session.ObjectAccess;
import io.hyperfoil.api.session.Session;

public class AgentDataImpl implements AgentData {
   private ConcurrentMap<String, Object> map = new ConcurrentHashMap<>();

   @Override
   public void push(Session session, String name, Object obj) {
      if (map.putIfAbsent(name, obj) != null) {
         session.fail(new BenchmarkExecutionException("Trying to push global data '" + name + "' second time."));
      }
   }

   @Override
   public void pull(Session session, String name, ObjectAccess access) {
      Object obj = map.get(name);
      if (obj == null) {
         session.fail(new BenchmarkExecutionException("Trying to pull global data '" + name + "' but it is not defined; maybe you need to synchronize phases with 'startAfterStrict'?"));
      }
      access.setObject(session, obj);
   }
}
