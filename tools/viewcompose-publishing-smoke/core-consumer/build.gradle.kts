plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(17)
}

val viewComposeGroup = rootProject.extra["viewComposeGroup"] as String
@Suppress("UNCHECKED_CAST")
val viewComposeVersions = rootProject.extra["viewComposeVersions"] as Map<String, String>
fun viewCompose(module: String): String =
    "$viewComposeGroup:$module:${checkNotNull(viewComposeVersions[module])}"

dependencies {
    implementation(viewCompose("viewcompose-navigation-core"))
    implementation(viewCompose("viewcompose-animation-core"))
    implementation(viewCompose("viewcompose-gesture-core"))
    implementation(viewCompose("viewcompose-graphics-core"))
}
