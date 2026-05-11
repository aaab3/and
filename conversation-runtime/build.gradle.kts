plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core-result"))
    implementation(project(":conversation-data"))
    implementation(project(":binding-data"))
    implementation(project(":binding-runtime"))
    implementation(project(":model-provider"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    testImplementation(project(":binding-data-sqlite-jvm"))
    testImplementation(project(":conversation-data-sqlite-jvm"))
    testImplementation(project(":conversation-store-core"))
    testImplementation(project(":secret-api"))
    testImplementation(project(":secret-sqlite-jvm"))
    testImplementation(kotlin("test"))
    testImplementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

tasks.test {
    useJUnitPlatform()
}
