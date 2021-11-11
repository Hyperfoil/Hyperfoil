package io.hyperfoil.core.session;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import io.hyperfoil.api.BenchmarkExecutionException;
import io.hyperfoil.api.session.GlobalData;
import io.hyperfoil.api.session.ObjectAccess;
import io.hyperfoil.api.session.Session;

public class GlobalDataImpl implements GlobalData {
   private ConcurrentMap<String, Object> map = new ConcurrentHashMap<>();

   @Override
   public void push(Session session, String name, Object obj) {
      if (obj instanceof ObjectVar[]) {
         ObjectVar[] array = (ObjectVar[]) obj;
         Object[] newArray = new Object[getLength(array)];
         obj = newArray;
         for (int i = 0; i < newArray.length; ++i) {
            if (array[i].isSet()) {
               newArray[i] = array[i].objectValue(session);
            }
         }
      } else if (obj instanceof IntVar[]) {
         IntVar[] array = (IntVar[]) obj;
         Integer[] newArray = new Integer[getLength(array)];
         obj = newArray;
         for (int i = 0; i < newArray.length; ++i) {
            if (array[i].isSet()) {
               newArray[i] = array[i].intValue(session);
            }
         }
      }
      if (map.putIfAbsent(name, obj) != null) {
         session.fail(new BenchmarkExecutionException("Trying to push global data '" + name + "' second time."));
      }
   }

   private int getLength(Session.Var[] array) {
      int length = 0;
      for (int i = array.length - 1; i >= 0; --i) {
         if (array[i].isSet()) {
            length = i + 1;
            break;
         }
      }
      return length;
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
