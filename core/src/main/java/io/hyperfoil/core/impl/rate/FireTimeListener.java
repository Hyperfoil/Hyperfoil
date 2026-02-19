package io.hyperfoil.core.impl.rate;

@FunctionalInterface
public interface FireTimeListener {

   void onFireTime(long fireTimeNs);

}
