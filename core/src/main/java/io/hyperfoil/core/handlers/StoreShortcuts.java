package io.hyperfoil.core.handlers;

import io.hyperfoil.api.config.BuilderBase;
import io.hyperfoil.api.processor.Processor;
import io.hyperfoil.core.data.DataFormat;

public class StoreShortcuts<P extends StoreShortcuts.Host> implements BuilderBase<StoreShortcuts<P>> {
   private final P parent;
   private DataFormat format = DataFormat.STRING;
   private String toVar;
   private String toArray;

   public interface Host {
      void accept(Processor.Builder processor);
   }

   public StoreShortcuts(P parent) {
      this.parent = parent;
   }

   /**
    * Conversion to apply on the matching parts with 'toVar' or 'toArray' shortcuts.
    *
    * @param format Data format.
    * @return Self.
    */
   public StoreShortcuts<P> format(DataFormat format) {
      this.format = format;
      return this;
   }


   /**
    * Shortcut to store selected parts in an array in the session. Must follow the pattern <code>variable[maxSize]</code>
    *
    * @param varAndSize Array name.
    * @return Self.
    */
   public StoreShortcuts<P> toArray(String varAndSize) {
      this.toArray = varAndSize;
      return this;
   }

   /**
    * Shortcut to store first match in given variable. Further matches are ignored.
    *
    * @param var Variable name.
    * @return Self.
    */
   public StoreShortcuts<P> toVar(String var) {
      this.toVar = var;
      return this;
   }

   public P end() {
      return parent;
   }

   @Override
   public void prepareBuild() {
      if (toArray != null) {
         parent.accept(new ArrayRecorder.Builder().init(toArray).format(format));
      }
      if (toVar != null) {
         parent.accept(new StoreProcessor.Builder().toVar(toVar).format(format));
      }
   }
}
