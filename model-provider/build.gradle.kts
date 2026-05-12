plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core-result"))
    implementation(project(":binding-data"))
    implementation(project(":binding-runtime"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // OpenAI Kotlin SDK
    implementation(platform("com.aallam.openai:openai-client-bom:4.1.0"))
    implementation("com.aallam.openai:openai-client")
    runtimeOnly("io.ktor:ktor-client-okhttp:3.0.1")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
