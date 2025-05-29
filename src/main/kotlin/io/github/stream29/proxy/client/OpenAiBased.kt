package io.github.stream29.proxy.client

import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Suppress("unused")
@Serializable
@SerialName("DashScope")
data class DashScopeConfig(
    val apiKey: String,
    val modelList: List<String> = listOf("qwen-max", "qwen-plus", "qwen-turbo", "qwen-long"),
) : ApiProvider by OpenAiConfig(
    baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1/",
    apiKey = apiKey,
    modelList = modelList
)

@Suppress("unused")
@Serializable
@SerialName("DeepSeek")
data class DeepSeekConfig(
    val apiKey: String,
    val modelList: List<String> = listOf("deepseek-chat", "deepseek-reasoner"),
) : ApiProvider by OpenAiConfig(
    baseUrl = "https://api.deepseek.com/",
    apiKey = apiKey,
    modelList = modelList
).messageMergedByRole()

@Suppress("unused")
@Serializable
@SerialName("Mistral")
data class MistralConfig(
    val apiKey: String,
    val modelList: List<String> = listOf("codestral", "mistral-large"),
) : ApiProvider by OpenAiConfig(
    baseUrl = "https://api.mistral.ai/v1/",
    apiKey = apiKey,
    modelList = modelList
)

@Suppress("unused")
@Serializable
@SerialName("SiliconFlow")
data class SiliconFlowConfig(
    val apiKey: String,
    val modelList: List<String>,
) : ApiProvider by OpenAiConfig(
    baseUrl = "https://api.siliconflow.cn/v1/",
    apiKey = apiKey,
    modelList = modelList
)

@Suppress("unused")
@Serializable
@SerialName("Gemini")
data class GeminiConfig(
    val apiKey: String,
    val modelList: List<String>,
) : ApiProvider by OpenAiConfig(
    baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai",
    apiKey = apiKey,
    modelList = modelList
)

@Suppress("unused")
@Serializable
@SerialName("Claude")
data class ClaudeConfig(
    val apiKey: String,
    val modelList: List<String>,
) : ApiProvider by OpenAiConfig(
    baseUrl = "https://api.anthropic.com/v1/",
    apiKey = apiKey,
    modelList = modelList
)

@Suppress("unused")
@Serializable
@SerialName("OpenRouter")
data class OpenRouterConfig(
    val apiKey: String,
    val modelList: List<String>,
) : ApiProvider by OpenAiConfig(
    baseUrl = "https://openrouter.ai/api/v1",
    apiKey = apiKey,
    modelList = modelList
)

@Suppress("unused")
@Serializable
@SerialName("AzureOpenAI")
data class AzureOpenAiConfig(
    val endPoint: String,
    val apiKey: String,
    val apiVersion: String = "2024-02-01",
    val deploymentId: String,
) : ApiProvider by OpenAiConfig(
    baseUrl = endPoint,
    apiKey = apiKey,
    modelList = listOf(deploymentId),
    buildHttpRequest = {
        url {
            appendPathSegments("openai", "deployments", deploymentId, "chat", "completions")
            parameter("api-version", apiVersion)
        }
        setBody(it)
        contentType(ContentType.Application.Json)
        accept(ContentType.Text.EventStream)
        headers {
            append(HttpHeaders.CacheControl, "no-cache")
            append(HttpHeaders.Connection, "keep-alive")
            append("api-key", apiKey)
        }
    }
)
