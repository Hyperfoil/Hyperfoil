package io.hyperfoil.hotrod.config;

import io.hyperfoil.api.config.Ergonomics;

public class HotRodErgonomics extends Ergonomics {

   private final HotRodPluginBuilder parent;

   public HotRodErgonomics(HotRodPluginBuilder parent) {
      this.parent = parent;
   }

   public HotRodPluginBuilder endErgonomics() {
      return parent;
   }
}
