import java.util.Properties

plugins {
    id("com.android.library") version "8.13.2" apply false
    kotlin("android") version "2.0.21" apply false
    kotlin("jvm") version "2.0.21" apply false
}

val publishingPropertiesFile = rootProject.file("../../gradle/viewcompose-publishing.properties")
val publishingProperties = Properties().apply {
    publishingPropertiesFile.inputStream().use(::load)
}
val viewComposeGroup = providers.gradleProperty("viewComposeGroup")
    .orElse(publishingProperties.getProperty("maven.group"))
    .get()
val viewComposeVersions = publishingProperties.stringPropertyNames()
    .filter { key -> key.startsWith("module.") && key.endsWith(".version") }
    .associate { key ->
        val module = key.removePrefix("module.").removeSuffix(".version")
        val version = providers.gradleProperty("viewComposeVersion.$module")
            .orElse(publishingProperties.getProperty(key))
            .get()
        module to version
    }

extra["viewComposeGroup"] = viewComposeGroup
extra["viewComposeVersions"] = viewComposeVersions
