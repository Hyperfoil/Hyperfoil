package io.hyperfoil.core.steps.data;

import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.session.IntVar;
import io.hyperfoil.core.session.ObjectVar;

class SharedDataHelper {
   static Object unwrapVars(Session session, Object obj) {
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
      return obj;
   }

   private static int getLength(Session.Var[] array) {
      int length = 0;
      for (int i = array.length - 1; i >= 0; --i) {
         if (array[i].isSet()) {
            length = i + 1;
            break;
         }
      }
      return length;
   }
}
