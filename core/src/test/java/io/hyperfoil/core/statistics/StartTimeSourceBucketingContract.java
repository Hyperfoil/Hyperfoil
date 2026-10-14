package io.hyperfoil.core.statistics;

/**
 * Marker interface for tests that check bucketing logic.
 *
 * Ensures all stats for a single operation (like blocked time and responses)
 * land in the same time bucket. Each module (core, http, hotrod) implements this.
 */
public interface StartTimeSourceBucketingContract {
}
