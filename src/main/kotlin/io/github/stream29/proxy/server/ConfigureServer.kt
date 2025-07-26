package io.github.stream29.proxy.server

import io.github.stream29.proxy.*
import io.github.stream29.proxy.client.STREAM_END_TOKEN
import io.github.stream29.proxy.client.STREAM_PREFIX
import io.github.stream29.proxy.client.listModelNames
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.*
import org.slf4j.Logger
import org.slf4j.event.Level

fun createLmStudioServer(config: LmStudioConfig): EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration> =
    embeddedServer(
        factory = CIO,
        environment = applicationEnvironment { log = lmStudioLogger.filterKtorLogging() },
        port = config.port,
        host = config.host
    )
    {
        configureServerCommon(lmStudioLogger)
        routing {
            route(config.path) {
                get("/api/v0/models") {
                    call.respond<LModelResponse>(LModelResponse(apiProviders.listModelNames().map { LModel(it) }))
                }
                post("/api/v0/chat/completions") {
                    val request = call.receive<LChatCompletionRequest>()
                    val apiProvider = apiProviders[request.model.substringBefore('/')]
                    if (apiProvider == null) {
                        call.respond<HttpStatusCode>(HttpStatusCode.NotFound)
                        return@post
                    }
                    val requestWithOriginalModelName = request.copy(model = request.model.substringAfter('/'))
                    call.respondChatSSE<LChatCompletionResponseChunk>(
                        streamPrefix = STREAM_PREFIX,
                        streamEndToken = STREAM_END_TOKEN,
                        apiProvider.generateLStream(requestWithOriginalModelName)
                    )
                }
            }

        }
    }

fun createOllamaServer(config: OllamaConfig): EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration> =
    embeddedServer(
        factory = CIO,
        environment = applicationEnvironment { log = ollamaLogger.filterKtorLogging() },
        port = config.port,
        host = config.host
    ) {
        configureServerCommon(ollamaLogger)
        routing {
            route(config.path) {
                get("/") {
                    call.respond<String>("Ollama is running")
                }
                get("/api/tags") {
                    call.respond<OModelResponse>(
                        apiProviders
                            .listModelNames()
                            .map { OModel(it) }
                            .let { OModelResponse(it) }
                    )
                }
                post("/api/chat") {
                    val request = call.receive<OChatRequest>()
                    val apiProvider = apiProviders[request.model.substringBefore('/')]
                    if (apiProvider == null) {
                        call.respond<HttpStatusCode>(HttpStatusCode.NotFound)
                        return@post
                    }
                    val requestWithOriginalModelName = request.copy(model = request.model.substringAfter('/'))
                    call.respondChatSSE<OChatResponseChunk>(
                        streamPrefix = "",
                        streamEndToken = "",
                        apiProvider.generateOStream(requestWithOriginalModelName),
                    )
                }
                post("/api/show") {
                    call.receiveText()
                    call.respond(mockOModelInfoResponse)
                }
            }
        }
    }

private fun Application.configureServerCommon(callLogger: Logger) {
    install(ContentNegotiation) {
        json(globalJson)
    }

    install(CallLogging) {
        level = Level.INFO
        logger = callLogger
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respondText(text = cause.stackTraceToString(), status = HttpStatusCode.InternalServerError)
            callLogger.error("Error processing request.", cause)
        }
    }
}

private val mockOModelInfoResponse = buildJsonObject {
    put("modelfile", "Mock modelfile")
    put("parameters", "Mock parameters")
    put("template", "Mock template")
    putJsonObject("details") {
        put("parent_model", "")
        put("format", "gguf")
        put("family", "llama")
        putJsonArray("families") { add("llama") }
        put("parameter_size", "8.0B")
        put("quantization_level", "Q4_0")
    }
    putJsonObject("model_info") {
        put("general.architecture", "llama")
        put("general.file_type", 2)
        put("general.parameter_count", 8030261248L)
        put("general.quantization_version", 2)
        put("llama.attention.head_count", 32)
        put("llama.attention.head_count_kv", 8)
        put("llama.attention.layer_norm_rms_epsilon", 0.00001)
        put("llama.block_count", 32)
        put("llama.context_length", 8192)
        put("llama.embedding_length", 4096)
        put("llama.feed_forward_length", 14336)
        put("llama.rope.dimension_count", 128)
        put("llama.rope.freq_base", 500000)
        put("llama.vocab_size", 128256)
        put("tokenizer.ggml.bos_token_id", 128000)
        put("tokenizer.ggml.eos_token_id", 128009)
        putJsonArray("tokenizer.ggml.merges") {}
        put("tokenizer.ggml.model", "gpt2")
        put("tokenizer.ggml.pre", "llama-bpe")
        putJsonArray("tokenizer.ggml.token_type") {}
        putJsonArray("tokenizer.ggml.tokens") {}
    }
}