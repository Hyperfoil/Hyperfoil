package io.hyperfoil.core.util;

import java.util.BitSet;

import io.hyperfoil.api.session.Session;

public class BitSetResource extends BitSet implements Session.Resource {
   public BitSetResource(int nbits) {
      super(nbits);
   }
}
