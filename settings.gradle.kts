import java.util.Properties

pluginManagement {
    repositories {
        google()
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        mavenCentral()
    }
}

rootProject.name = "runtime"
include(
    "core-result",
    "conversation-data",
    "conversation-store-core",
    "conversation-data-sqlite-jvm",
    "binding-data",
    "binding-data-sqlite-jvm",
    "secret-api",
    "secret-sqlite-jvm",
    "binding-runtime",
    "model-provider",
    "conversation-runtime",
    "skill-package",
    "workflow-definition",
    "tool-runtime",
    "knowledge",
    "workflow-execution"
)

val sdkProps = Properties()
val localPropsFile = file("local.properties")
if (localPropsFile.exists()) {
    localPropsFile.inputStream().use { sdkProps.load(it) }
}
val sdkDir =
    sdkProps.getProperty("sdk.dir")?.replace("\\\\", "\\")
        ?: System.getenv("ANDROID_HOME")
        ?: System.getenv("ANDROID_SDK_ROOT")
if (!sdkDir.isNullOrBlank()) {
    include("android-app")
}
