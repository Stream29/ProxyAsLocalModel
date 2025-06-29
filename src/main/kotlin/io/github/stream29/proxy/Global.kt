package io.github.stream29.proxy

import com.charleskorn.kaml.*
import io.github.stream29.proxy.client.listModelNames
import io.github.stream29.proxy.server.createLmStudioServer
import io.github.stream29.proxy.server.createOllamaServer
import io.github.stream29.streamlin.AutoUpdateMode
import io.github.stream29.streamlin.AutoUpdatePropertyRoot
import io.github.stream29.streamlin.getValue
import io.github.stream29.streamlin.setValue
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File

val helpLogger = LoggerFactory.getLogger("Help")!!
val configLogger = LoggerFactory.getLogger("Config")!!
val modelListLogger = LoggerFactory.getLogger("Model List")!!
val lmStudioLogger = LoggerFactory.getLogger("LM Studio Server")!!
val ollamaLogger = LoggerFactory.getLogger("Ollama Server")!!
val clientLogger = LoggerFactory.getLogger("Ktor Client")!!

val globalJson = Json {
    prettyPrint = false
    isLenient = true
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

val globalYaml = Yaml(
    configuration = YamlConfiguration(
        polymorphismStyle = PolymorphismStyle.Property,
        strictMode = false,
        singleLineStringStyle = SingleLineStringStyle.PlainExceptAmbiguous,
        multiLineStringStyle = MultiLineStringStyle.Literal,
        encodeDefaults = false
    )
)

val configFile = File("config.yml")

@Suppress("unused")
val unused = {
    if (!configFile.exists()) {
        helpLogger.info("It looks that you are starting the program for the first time here.")
        helpLogger.info("A default config file is created at ${configFile.absolutePath} with schema annotation.")
        configFile.writeText(
            """
# ${'$'}schema: https://github.com/Stream29/ProxyAsLocalModel/raw/master/config_v5.schema.json
lmStudio:
  port: 1234
ollama:
  port: 11434
apiProviders: {}
"""
        )
    }
    watch(configFile) { file ->
        if (!file.exists())
            return@watch
        val text = file.readText()
        try {
            val previousConfig = config
            val newConfig = text.decodeYaml<Config>()
            if (previousConfig != newConfig) {
                config = newConfig
            }
            if (previousConfig.apiProviders != newConfig.apiProviders) {
                val previousApiProviders = apiProviders
                previousApiProviders.values.forEach { it.close() }
                apiProviderProperty.set(newConfig.apiProviders)
            }
            if (previousConfig.client != newConfig.client) {
                val previousClient = globalClient
                previousClient.close()
                clientConfigProperty.set(newConfig.client)
            }
            if (previousConfig.lmStudio != newConfig.lmStudio) {
                val previousServer = lmStudioServer
                previousServer?.stop()
                lmStudioConfigProperty.set(newConfig.lmStudio)
            }
            if (previousConfig.ollama != newConfig.ollama) {
                val previousServer = ollamaServer
                previousServer?.stop()
                ollamaConfigProperty.set(newConfig.ollama)
            }
        } catch (e: SerializationException) {
            configLogger.error("${e.message}")
        }
    }
    configLogger.info("Config file watcher started at ${configFile.absolutePath}")
}()

private val configProperty = AutoUpdatePropertyRoot(
    sync = true,
    mode = AutoUpdateMode.PROPAGATE,
    initValue = configFile.readText().decodeYaml<Config>()
)

var config by configProperty

val clientConfigProperty = AutoUpdatePropertyRoot(
    sync = true,
    mode = AutoUpdateMode.PROPAGATE,
    initValue = config.client
)

val globalClient by clientConfigProperty.subproperty {
    configLogger.info("Ktor Client created with: $it")
    HttpClient(io.ktor.client.engine.cio.CIO) {
        install(ContentNegotiation) {
            json(globalJson)
        }
        install(HttpTimeout) {
            socketTimeoutMillis = it.socketTimeout
            connectTimeoutMillis = it.connectTimeout
            requestTimeoutMillis = it.requestTimeout
        }
        install(HttpRequestRetry) {
            retryOnException(maxRetries = it.retry)
            constantDelay(it.delayBeforeRetry)
        }
        expectSuccess = true
    }
}

private val apiProviderProperty = AutoUpdatePropertyRoot(
    sync = true,
    mode = AutoUpdateMode.PROPAGATE,
    initValue = config.apiProviders
)

@OptIn(DelicateCoroutinesApi::class)
val apiProviders by apiProviderProperty.subproperty {
    GlobalScope.launch { modelListLogger.info("Model list loaded with: ${it.listModelNames()}") }
    it
}

private val lmStudioConfigProperty = AutoUpdatePropertyRoot(
    sync = true,
    mode = AutoUpdateMode.PROPAGATE,
    initValue = config.lmStudio
)

val lmStudioServer by lmStudioConfigProperty.subproperty { config ->
    if (config.enabled) {
        lmStudioLogger.info("LM Studio Server started at ${config.port}")
        createLmStudioServer(config).apply { start(wait = false) }
    } else null
}

private val ollamaConfigProperty = AutoUpdatePropertyRoot(
    sync = true,
    mode = AutoUpdateMode.PROPAGATE,
    initValue = config.ollama
)

val ollamaServer by ollamaConfigProperty.subproperty {
    if (it.enabled) {
        ollamaLogger.info("Ollama Server started at ${it.port}")
        createOllamaServer(it).apply { start(wait = false) }
    } else null
}

inline fun <reified T> String.decodeYaml() = globalYaml.decodeFromString<T>(this)
inline fun <reified T> T.encodeYaml() = globalYaml.encodeToString(this)
inline fun <reified T> T.encodeJson() = globalJson.encodeToString(this)