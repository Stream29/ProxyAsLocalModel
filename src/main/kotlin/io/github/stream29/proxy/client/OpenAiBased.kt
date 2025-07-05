package io.github.stream29.proxy.client

import io.github.stream29.proxy.ModelConfig
import io.github.stream29.proxy.server.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Suppress("unused")
@Serializable
@SerialName("DashScope")
data class DashScopeConfig(
    val apiKey: String,
    val modelList: List<ModelConfig> =
        listOf(
            ModelConfig(name = "qwen-max"),
            ModelConfig(name = "qwen-plus"),
            ModelConfig(name = "qwen-turbo"),
            ModelConfig(name = "qwen-long"),
        ),
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
    val modelList: List<ModelConfig> =
        listOf(
            ModelConfig(name = "deepseek-chat"),
            ModelConfig(name = "deepseek-reasoner"),
        ),
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
    val modelList: List<ModelConfig> =
        listOf(
            ModelConfig(name = "codestral"),
            ModelConfig(name = "mistral-large"),
        ),
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
    val modelList: List<ModelConfig>,
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
    val modelList: List<ModelConfig>,
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
    val modelList: List<ModelConfig>,
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
    val modelList: List<ModelConfig>,
) : ApiProvider by OpenAiConfig(
    baseUrl = "https://openrouter.ai/api/v1",
    apiKey = apiKey,
    modelList = modelList
)