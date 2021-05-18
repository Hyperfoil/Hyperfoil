package io.hyperfoil.core.steps;

import java.util.Collections;
import java.util.List;

import org.kohsuke.MetaInfServices;

import io.hyperfoil.api.config.InitFromParam;
import io.hyperfoil.api.config.Name;
import io.hyperfoil.api.config.Step;
import io.hyperfoil.api.config.StepBuilder;
import io.hyperfoil.api.session.ReadAccess;
import io.hyperfoil.api.session.Session;
import io.hyperfoil.core.session.SessionFactory;

public class AwaitVarStep extends DependencyStep {
   public AwaitVarStep(ReadAccess var) {
      super(new ReadAccess[]{ var });
   }

   public static class NegatedAccess implements ReadAccess {
      private final ReadAccess access;

      public NegatedAccess(ReadAccess access) {
         this.access = access;
      }

      @Override
      public boolean isSet(Session session) {
         return !access.isSet(session);
      }

      @Override
      public Object getObject(Session session) {
         throw new UnsupportedOperationException();
      }

      @Override
      public int getInt(Session session) {
         throw new UnsupportedOperationException();
      }

      @Override
      public Session.Var getVar(Session Session) {
         throw new UnsupportedOperationException();
      }

      @Override
      public Object key() {
         return access.key();
      }

      @Override
      public void setIndex(int index) {
         access.setIndex(index);
      }

      @Override
      public int index() {
         return access.index();
      }

      @Override
      public boolean isSequenceScoped() {
         return access.isSequenceScoped();
      }
   }

   /**
    * Block current sequence until this variable gets set/unset.
    */
   @MetaInfServices
   @Name("awaitVar")
   public static class Builder implements StepBuilder<Builder>, InitFromParam<Builder> {
      private String var;

      /**
       * @param param Variable name or <code>!variable</code> if we are waiting for it to be unset.
       * @return Self.
       */
      @Override
      public Builder init(String param) {
         return var(param);
      }

      /**
       * Variable name or <code>!variable</code> if we are waiting for it to be unset.
       *
       * @param var Name of the variable we're waiting for.
       * @return Self.
       */
      public Builder var(String var) {
         this.var = var;
         return this;
      }

      @Override
      public List<Step> build() {
         ReadAccess access;
         if (var.startsWith("!")) {
            access = new NegatedAccess(SessionFactory.readAccess(var.substring(1).trim()));
         } else {
            access = SessionFactory.readAccess(var);
         }
         return Collections.singletonList(new AwaitVarStep(access));
      }
   }
}
