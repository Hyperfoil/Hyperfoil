package io.hyperfoil.api.config;

/**
 * Allow to set up the builder from single string param.
 */
public interface InitFromParam<S extends InitFromParam<S>> {
   S init(String param);
}
