package io.github.stream29.proxy.client

import io.github.stream29.proxy.clientLogger
import io.github.stream29.proxy.encodeYaml
import io.github.stream29.proxy.relocate.com.aallam.openai.api.chat.ChatCompletionChunk
import io.github.stream29.proxy.relocate.com.aallam.openai.api.chat.ChatCompletionRequest
import io.github.stream29.proxy.relocate.com.aallam.openai.api.chat.ChatDelta
import io.github.stream29.proxy.relocate.com.aallam.openai.api.chat.ChatMessage
import io.github.stream29.proxy.relocate.com.aallam.openai.api.core.Role
import io.github.stream29.proxy.relocate.com.aallam.openai.api.model.ModelId
import io.github.stream29.proxy.server.*
import io.ktor.client.request.*
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@SerialName("OpenAi")
@Serializable
data class OpenAiConfig(
    val baseUrl: String,
    val apiKey: String,
    val modelList: List<String>,
    val extraRequest: (ChatCompletionRequest) -> ChatCompletionRequest = { it },
    val buildHttpRequest: (@Contextual HttpRequestBuilder).(ChatCompletionRequest) -> Unit = {
        url { appendPathSegments("chat", "completions") }
        setBody(it)
        contentType(ContentType.Application.Json)
        accept(ContentType.Text.EventStream)
        headers {
            append(HttpHeaders.CacheControl, "no-cache")
            append(HttpHeaders.Connection, "keep-alive")
            append(HttpHeaders.Authorization, "Bearer $apiKey")
        }
    },
) : ApiProvider {
    override suspend fun getModelNameList(): List<String> = modelList
    override suspend fun generateLStream(request: LChatCompletionRequest): Flow<LChatCompletionResponseChunk> {
        return chatCompletionsRecording(
            extraRequest(request.asOpenAiRequest())
        ).map { it.asLChatCompletionResponseChunk() }
    }

    override suspend fun generateOStream(request: OChatRequest): Flow<OChatResponseChunk> {
        return chatCompletionsRecording(
            extraRequest(request.asOpenAiRequest())
        ).map { it.asOChatResponseChunk() }
    }

    override fun close() {}
}

private suspend fun OpenAiConfig.chatCompletionsRecording(
    request: ChatCompletionRequest,
): Flow<ChatCompletionChunk> {
    val recorder = GenerationRecorder(clientLogger)
    recorder.onRequest(request.encodeYaml())
    return createStreamingChatCompletion(
        baseUrl = baseUrl,
        request = request,
        buildHttpRequest = buildHttpRequest
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