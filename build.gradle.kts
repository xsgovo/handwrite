plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.protobuf) apply false
    alias(libs.plugins.room) apply false
}

val jvmModules = listOf(
    ":core:model",
    ":core:document",
)

val androidModules = listOf(
    ":app",
    ":core:data",
    ":core:rendering",
    ":core:designsystem",
    ":feature:editor",
    ":feature:library",
    ":feature:settings",
    ":feature:export",
)

tasks.register("verifyLocal") {
    group = "verification"
    description = "Runs all unit tests, Android lint, and debug/release application builds."
    dependsOn(jvmModules.map { "$it:test" })
    dependsOn(androidModules.map { "$it:testDebugUnitTest" })
    dependsOn(androidModules.map { "$it:lint" })
    dependsOn(":app:assembleDebug", ":app:assembleRelease")
}
