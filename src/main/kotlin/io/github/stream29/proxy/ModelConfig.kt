package io.github.stream29.proxy

import kotlinx.serialization.Serializable

/**
 * Configuration for a model.
 * Breaking change in v0.1.0: Simple string format no longer supported.
 * Use object format: { name: "model-name" } instead of "model-name"
 */
@Serializable
data class ModelConfig(
    val name: String,
    val temperature: Double? = null,
    val extraParameters: Map<String, String>? = null,
    val extraBody: Map<String, String>? = null,
) {
    // Convenience properties for backward compatibility
    val modelName: String get() = name
    val temperatureOverride: Double? get() = temperature
}
