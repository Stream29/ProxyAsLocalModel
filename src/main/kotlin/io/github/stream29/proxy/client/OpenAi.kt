package io.github.stream29.proxy.client

import io.github.stream29.proxy.ModelConfig
import io.github.stream29.proxy.clientLogger
import io.github.stream29.proxy.encodeYaml
import io.github.stream29.proxy.globalJson
import io.github.stream29.proxy.relocate.com.aallam.openai.api.chat.ChatCompletionChunk
import io.github.stream29.proxy.relocate.com.aallam.openai.api.chat.ChatCompletionRequest
import io.github.stream29.proxy.relocate.com.aallam.openai.api.chat.ChatDelta
import io.github.stream29.proxy.relocate.com.aallam.openai.api.chat.ChatMessage
import io.github.stream29.proxy.relocate.com.aallam.openai.api.core.Role
import io.github.stream29.proxy.relocate.com.aallam.openai.api.model.ModelId
import io.github.stream29.proxy.server.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*

/**
 * Convert a string value to the appropriate JsonElement type.
 * It tries to parse as a JSON object/array first, then falls back to primitive types.
 */
private fun String.toJsonElement(): JsonElement {
    // First, try to parse as a full JSON object or array
    try {
        val trimmed = this.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return globalJson.parseToJsonElement(trimmed)
        }
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            return globalJson.parseToJsonElement(trimmed)
        }
    } catch (e: SerializationException) {
        // Ignore if it's not a valid JSON object/array, proceed to primitive parsing
    }

    // Fallback to parsing as a primitive type
    // Try to parse as number first
    this.toLongOrNull()?.let { return JsonPrimitive(it) }
    this.toDoubleOrNull()?.let { return JsonPrimitive(it) }

    // Try to parse as boolean
    when (this.lowercase()) {
        "true" -> return JsonPrimitive(true)
        "false" -> return JsonPrimitive(false)
    }

    // Default to string
    return JsonPrimitive(this)
}

@SerialName("OpenAi")
@Serializable
data class OpenAiConfig(
    val baseUrl: String,
    val apiKey: String,
    val modelList: List<ModelConfig>,
    val extraRequest: (ChatCompletionRequest) -> ChatCompletionRequest = { it },
) : ApiProvider {
    // Store model configurations for lookup
    private val modelConfigs: Map<String, ModelConfig> by lazy {
        modelList.associateBy { it.modelName }
    }

    override suspend fun getModelNameList(): List<String> = modelList.map { it.modelName }

    override suspend fun generateLStream(request: LChatCompletionRequest): Flow<LChatCompletionResponseChunk> {
        val openAiRequest = request.asOpenAiRequest()
        val (modifiedRequest, extraTopLevelParams) = applyModelConfig(openAiRequest, request.model)
        return chatCompletionsRecording(extraRequest(modifiedRequest), extraTopLevelParams).map { it.asLChatCompletionResponseChunk() }
    }

    override suspend fun generateOStream(request: OChatRequest): Flow<OChatResponseChunk> {
        val openAiRequest = request.asOpenAiRequest()
        val (modifiedRequest, extraTopLevelParams) = applyModelConfig(openAiRequest, request.model)
        return chatCompletionsRecording(extraRequest(modifiedRequest), extraTopLevelParams).map { it.asOChatResponseChunk() }
    }

    private fun applyModelConfig(
        request: ChatCompletionRequest,
        modelName: String,
    ): Pair<ChatCompletionRequest, Map<String, JsonElement>?> {
        val modelConfig = modelConfigs[modelName]
        if (modelConfig == null) {
            clientLogger.debug("No model config found for model: $modelName")
            return request to null
        }

        var modifiedRequest = request
        var topLevelParams: Map<String, JsonElement>? = null

        // Apply temperature override if specified
        modelConfig.temperature?.let { tempOverride ->
            clientLogger.debug("Overriding temperature for model $modelName: ${request.temperature} -> $tempOverride")
            modifiedRequest = modifiedRequest.copy(temperature = tempOverride)
        }

        // Apply extraParameters (top-level)
        modelConfig.extraParameters?.let { extraParams ->
            clientLogger.debug("Applying extra parameters for model $modelName: $extraParams")

            // Handle known parameters that map to specific fields
            val knownParams = mutableMapOf<String, JsonElement>()
            val unknownParams = mutableMapOf<String, JsonElement>()

            extraParams.forEach { (key, value) ->
                val jsonValue = value.toJsonElement()
                when (key) {
                    "temperature" -> {
                        // Temperature should use the dedicated field, not extraParameters
                        clientLogger.warn(
                            "Temperature specified in extraParameters for model $modelName will be ignored. Use the dedicated 'temperature' field instead.",
                        )
                        knownParams[key] = jsonValue // Track but don't apply
                    }
                    "max_tokens" -> {
                        jsonValue.jsonPrimitive.intOrNull?.let { maxTokens ->
                            clientLogger.debug("Setting max_tokens to $maxTokens")
                            modifiedRequest = modifiedRequest.copy(maxTokens = maxTokens)
                            knownParams[key] = jsonValue
                        } ?: clientLogger.warn("Invalid value for max_tokens parameter: '$value' (expected integer)")
                    }
                    "top_p" -> {
                        jsonValue.jsonPrimitive.doubleOrNull?.let { topP ->
                            clientLogger.debug("Setting top_p to $topP")
                            modifiedRequest = modifiedRequest.copy(topP = topP)
                            knownParams[key] = jsonValue
                        } ?: clientLogger.warn("Invalid value for top_p parameter: '$value' (expected number between 0 and 1)")
                    }
                    "frequency_penalty" -> {
                        jsonValue.jsonPrimitive.doubleOrNull?.let { freqPenalty ->
                            clientLogger.debug("Setting frequency_penalty to $freqPenalty")
                            modifiedRequest = modifiedRequest.copy(frequencyPenalty = freqPenalty)
                            knownParams[key] = jsonValue
                        }
                            ?: clientLogger.warn(
                                "Invalid value for frequency_penalty parameter: '$value' (expected number between -2.0 and 2.0)",
                            )
                    }
                    "presence_penalty" -> {
                        jsonValue.jsonPrimitive.doubleOrNull?.let { presPenalty ->
                            clientLogger.debug("Setting presence_penalty to $presPenalty")
                            modifiedRequest = modifiedRequest.copy(presencePenalty = presPenalty)
                            knownParams[key] = jsonValue
                        }
                            ?: clientLogger.warn(
                                "Invalid value for presence_penalty parameter: '$value' (expected number between -2.0 and 2.0)",
                            )
                    }
                    "stop" -> {
                        // Handle stop sequences - can be a single string or array of strings
                        val trimmedValue = value.trim()
                        if (trimmedValue.startsWith("[") && trimmedValue.endsWith("]")) {
                            // Try to parse as JSON array
                            try {
                                val stopList = globalJson.decodeFromString<List<String>>(trimmedValue)
                                clientLogger.debug("Setting stop to list: $stopList")
                                modifiedRequest = modifiedRequest.copy(stop = stopList)
                                knownParams[key] = JsonArray(stopList.map { JsonPrimitive(it) })
                            } catch (e: SerializationException) {
                                // If parsing fails, treat as single string value
                                clientLogger.warn(
                                    "Failed to parse stop parameter as JSON array: '$value', treating as single string. Error: ${e.message}",
                                )
                                modifiedRequest = modifiedRequest.copy(stop = listOf(value))
                                knownParams[key] = jsonValue
                            } catch (e: IllegalArgumentException) {
                                // Some JSON parsing errors might throw IllegalArgumentException
                                clientLogger.warn(
                                    "Failed to parse stop parameter as JSON array: '$value', treating as single string. Error: ${e.message}",
                                )
                                modifiedRequest = modifiedRequest.copy(stop = listOf(value))
                                knownParams[key] = jsonValue
                            }
                        } else {
                            // Single string
                            clientLogger.debug("Setting stop to single value: $value")
                            modifiedRequest = modifiedRequest.copy(stop = listOf(value))
                            knownParams[key] = jsonValue
                        }
                    }
                    "seed" -> {
                        jsonValue.jsonPrimitive.intOrNull?.let { seed ->
                            clientLogger.debug("Setting seed to $seed")
                            modifiedRequest = modifiedRequest.copy(seed = seed)
                            knownParams[key] = jsonValue
                        } ?: clientLogger.warn("Invalid value for seed parameter: '$value' (expected integer)")
                    }
                    "n" -> {
                        jsonValue.jsonPrimitive.intOrNull?.let { n ->
                            clientLogger.debug("Setting n to $n")
                            modifiedRequest = modifiedRequest.copy(n = n)
                            knownParams[key] = jsonValue
                        } ?: clientLogger.warn("Invalid value for n parameter: '$value' (expected positive integer)")
                    }
                    else -> {
                        // Unknown parameters go to top-level
                        unknownParams[key] = jsonValue
                    }
                }
            }

            if (unknownParams.isNotEmpty()) {
                clientLogger.debug("Setting top-level parameters: ${unknownParams.keys}")
                topLevelParams = unknownParams
            }
        }

        // Apply extraBody (nested)
        modelConfig.extraBody?.let { extraBodyParams ->
            if (extraBodyParams.isNotEmpty()) {
                clientLogger.debug("Applying extra body parameters for model $modelName: $extraBodyParams")

                // Merge with existing extraBody if present
                val existingExtra =
                    when (val extra = request.extraBody) {
                        is JsonObject -> extra.jsonObject
                        null -> emptyMap()
                        else -> {
                            clientLogger.warn("Existing extraBody is not a JsonObject, it will be replaced")
                            emptyMap()
                        }
                    }
                val mergedExtra =
                    buildJsonObject {
                        // Add existing extra body fields
                        existingExtra.forEach { (key, value) ->
                            put(key, value)
                        }
                        // Add extra body parameters
                        extraBodyParams.forEach { (key, value) ->
                            put(key, value.toJsonElement())
                        }
                    }
                clientLogger.debug("Setting extraBody with parameters: ${extraBodyParams.keys}")
                modifiedRequest = modifiedRequest.copy(extraBody = mergedExtra)
            }
        }

        return modifiedRequest to topLevelParams
    }

    override fun close() {}
}

private suspend fun OpenAiConfig.chatCompletionsRecording(
    request: ChatCompletionRequest,
    extraTopLevelParams: Map<String, JsonElement>? = null,
): Flow<ChatCompletionChunk> {
    val recorder = GenerationRecorder(clientLogger)
    val requestStr = globalJson.encodeToString(ChatCompletionRequest.serializer(), request)
    recorder.onRequest(requestStr)
    return createStreamingChatCompletion(
        baseUrl = baseUrl,
        apiKey = apiKey,
        request = request,
        extraTopLevelParams = extraTopLevelParams,
    ).onEach { chunk ->
        chunk.choices.firstOrNull()?.delta?.run {
            content?.let { recorder.onPartialOutput(it) }
            reasoningContent?.let { recorder.onPartialReasoning(it) }
        }
    }.onCompletion {
        if (it != null) recorder.dumpOnError(it)
        else recorder.dump()
    }
}

private fun OChatRequest.asOpenAiRequest() =
    ChatCompletionRequest(
        model = ModelId(model),
        messages = messages.map { it.asOpenAiMessage() },
        temperature = options.temperature,
    )

private fun OChatMessage.asOpenAiMessage() =
    ChatMessage(
        role = role.asOpenAiRole(),
        content = content
    )

private fun ChatCompletionChunk.asOChatResponseChunk() =
    OChatResponseChunk(
        model = model.id,
        message = OChatMessage(
            role = "assistant",
            content = choices.firstOrNull()?.delta?.content ?: "",
        ),
        done = choices.firstOrNull()?.finishReason != null,
    )

private fun String.asOpenAiRole(): Role = when (this) {
    "user" -> Role.User
    "assistant" -> Role.Assistant
    "system" -> Role.System
    "tool" -> Role.Tool
    "function" -> Role.Function
    else -> throw IllegalArgumentException("Unsupported role: $this")
}

private fun LChatCompletionRequest.asOpenAiRequest() =
    ChatCompletionRequest(
        model = ModelId(model),
        messages = messages.map { it.asOpenAiMessage() },
        temperature = temperature,
    )

private fun LChatMessage.asOpenAiMessage() =
    ChatMessage(
        role = role.asOpenAiRole(),
        content = content
    )

private fun ChatCompletionChunk.asLChatCompletionResponseChunk() =
    LChatCompletionResponseChunk(
        id = id ?: "null",
        model = model.id,
        choices = choices.map {
            LChatCompletionChoice(
                index = it.index,
                delta = it.delta!!.asLChatMessage(),
                finishReason = it.finishReason?.value,
            )
        }
    )

private fun ChatDelta.asLChatMessage() =
    LChatMessage(
        role = role?.role ?: "assistant",
        content = content ?: "",
    )