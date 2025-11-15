plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.graalvm.buildtools)
    alias(libs.plugins.gradleup.shadow)
    alias(libs.plugins.kotlin.serialization)
    application
}

group = "io.github.stream29"
version = "0.0.8"

repositories {
    mavenCentral()
}

dependencies {
    // Ktor server
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.host.common)
    implementation(libs.ktor.server.status.pages)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.client.content.negotiation)

    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    
    implementation(libs.logback.classic)
    
    implementation(libs.streamlin)
    implementation(libs.json.schema.generator)
    implementation(libs.kaml)

    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("io.github.stream29.proxy.MainKt")
}

graalvmNative {
    binaries {
        named("main") {
            imageName.set("Proxy")
            mainClass.set("io.github.stream29.proxy.MainKt")
            buildArgs("--enable-native-access=ALL-UNNAMED")
        }
    }
}

tasks.named<Jar>("jar") {
    // only the fat “-all.jar” is needed, so we disable the plain jar
    // this also helps to copy the jar file in docker without knowing its filename
    enabled = false
}
