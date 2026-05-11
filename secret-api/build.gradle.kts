plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core-result"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
}
