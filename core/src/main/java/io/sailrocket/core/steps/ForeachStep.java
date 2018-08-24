package io.sailrocket.core.steps;

import io.sailrocket.api.Session;
import io.sailrocket.core.api.ResourceUtilizer;
import io.sailrocket.core.session.SequenceImpl;
import io.sailrocket.core.session.SimpleVarReference;

public class ForeachStep extends BaseStep implements ResourceUtilizer {
   private final String dataVar;
   private final String counterVar;
   private final SequenceImpl template;

   public ForeachStep(String dataVar, String counterVar, SequenceImpl template) {
      this.dataVar = dataVar;
      this.counterVar = counterVar;
      this.template = template;
      addDependency(new SimpleVarReference(dataVar));
   }

   @Override
   public void invoke(Session session) {
      Object value = session.getObject(dataVar);
      if (!(value instanceof io.sailrocket.api.Session.Var[])) {
         throw new IllegalStateException("Variable " + dataVar + " does not contain var array: " + value);
      }
      // Java array polymorphism is useful at times...
      io.sailrocket.api.Session.Var[] array = (io.sailrocket.api.Session.Var[]) value;
      int i = 0;
      for (; i < array.length; i++) {
         if (!array[i].isSet()) break;
         template.instantiate(session, i);
      }
      if (counterVar != null) {
         session.setInt(counterVar, i);
      }
   }

   @Override
   public void reserve(Session session) {
      session.declareInt(counterVar);
   }
}
