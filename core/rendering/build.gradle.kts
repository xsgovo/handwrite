plugins {
    id("handwrite.android.library")
    id("handwrite.android.compose")
}

android {
    namespace = "com.xsgovo.handwrite.core.rendering"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:document"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.ink.rendering)
    implementation(libs.androidx.ink.brush)
    implementation(libs.androidx.ink.strokes)

    testImplementation(libs.junit4)
}
