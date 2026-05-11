plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core-result"))
    implementation(project(":binding-data"))
    implementation(project(":secret-api"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    testImplementation(project(":binding-data-sqlite-jvm"))
    testImplementation(project(":secret-sqlite-jvm"))
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
