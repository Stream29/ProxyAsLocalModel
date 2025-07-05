package io.github.stream29.proxy.client

import io.github.stream29.proxy.globalClient
import io.github.stream29.proxy.globalJson
import io.github.stream29.proxy.relocate.com.aallam.openai.api.chat.ChatCompletionChunk
import io.github.stream29.proxy.relocate.com.aallam.openai.api.chat.ChatCompletionRequest
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.*

suspend fun createStreamingChatCompletion(
    baseUrl: String,
    apiKey: String,
    request: ChatCompletionRequest,
    extraTopLevelParams: Map<String, JsonElement>? = null,
): Flow<ChatCompletionChunk> {
    // Create the request body, merging extra parameters if needed
    val requestBody =
        if (extraTopLevelParams.isNullOrEmpty()) {
            // No extra parameters, just use the request as-is
            request
        } else {
            // We need to merge extra parameters at the top level
            // First serialize the request to JSON
            val requestJsonElement = globalJson.encodeToJsonElement(ChatCompletionRequest.serializer(), request)

            // Convert to JsonObject and add extra parameters
            val mergedJson =
                buildJsonObject {
                    // Add all fields from the original request
                    requestJsonElement.jsonObject.forEach { (key, value) ->
                        put(key, value)
                    }
                    // Add extra top-level parameters
                    extraTopLevelParams.forEach { (key, value) ->
                        put(key, value)
                    }
                }

            // Return the merged JSON object as the body
            mergedJson
        }

    val statement =
        globalClient.preparePost(baseUrl) {
            url { appendPathSegments("chat", "completions") }
            when (requestBody) {
                is JsonObject -> {
                    // For JsonObject, use TextContent to ensure proper Content-Type handling
                    setBody(
                        io.ktor.http.content
                            .TextContent(requestBody.toString(), ContentType.Application.Json),
                    )
                }
                else -> {
                    // For regular objects, use normal serialization
                    setBody(requestBody)
                    contentType(ContentType.Application.Json)
                }
            }
            accept(ContentType.Text.EventStream)
            headers {
                append(HttpHeaders.CacheControl, "no-cache")
                append(HttpHeaders.Connection, "keep-alive")
                append(HttpHeaders.Authorization, "Bearer $apiKey")
            }
        }
    val channel = runCatching { statement.body<ByteReadChannel>() }
        .getOrElse { return flow { throw it } }
    return flow {
        while (currentCoroutineContext().isActive && !channel.isClosedForRead) {
            val line = channel.readUTF8Line()
            val value: ChatCompletionChunk = when {
                line == null -> break
                line.startsWith(STREAM_END_TOKEN) -> break
                line.startsWith(STREAM_PREFIX) -> line.decodeChunkNoReflection()
                else -> continue
            }
            emit(value)
        }
    }.onCompletion { channel.cancel() }
}

private fun String.decodeChunkNoReflection(): ChatCompletionChunk {
    return globalJson.decodeFromString(ChatCompletionChunk.serializer(), removePrefix(STREAM_PREFIX))
}

const val STREAM_PREFIX = "data:"
const val STREAM_END_TOKEN = "$STREAM_PREFIX [DONE]"