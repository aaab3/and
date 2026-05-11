plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core-result"))
    implementation(project(":workflow-definition"))
    implementation(project(":binding-data"))
    implementation(project(":binding-runtime"))
    implementation(project(":model-provider"))
    implementation(project(":tool-runtime"))
    implementation(project(":knowledge"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
