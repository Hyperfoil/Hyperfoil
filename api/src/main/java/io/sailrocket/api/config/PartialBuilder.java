package io.sailrocket.api.config;

/**
 * This builder is useful when we want to use custom keys in YAML.
 */
public interface PartialBuilder {
   Object withKey(String key);
}
