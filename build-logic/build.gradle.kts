plugins {
    `kotlin-dsl`
}

group = "com.xsgovo.handwrite.buildlogic"

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("com.android.tools.build:gradle:9.2.1")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.21")
    implementation("org.jetbrains.kotlin.plugin.compose:org.jetbrains.kotlin.plugin.compose.gradle.plugin:2.2.21")
    implementation("org.jetbrains.kotlin.plugin.serialization:org.jetbrains.kotlin.plugin.serialization.gradle.plugin:2.2.21")
}
